package server;

import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class MulticastListener implements Runnable {
    private final Database db;
    private final int myPort;
    private final String dbPath;

    public MulticastListener(Database db, int myPort, String dbPath) {
        this.db = db;
        this.myPort = myPort;
        this.dbPath = dbPath;
    }

    @Override
    public void run() {
        try (MulticastSocket socket = new MulticastSocket(3030)) {
            InetAddress group = InetAddress.getByName("230.30.30.30");
            socket.joinGroup(group);
            System.out.println("[Multicast] À escuta no 230.30.30.30:3030");
            byte[] buffer = new byte[8192];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());

                if (msg.startsWith("HEARTBEAT")) {
                    String[] parts = msg.split(";");
                    int remoteTcpPort = Integer.parseInt(parts[2]);
                    if (remoteTcpPort == myPort) continue; // Ignora o próprio heartbeat

                    int remoteVersion = Integer.parseInt(parts[1]);
                    int localVersion = db.getVersao();

                    if (remoteVersion > localVersion) {
                        System.out.println("[Multicast] Versão remota (v" + remoteVersion + ") > local (v" + localVersion + "). A sincronizar ficheiro...");
                        String remoteIp = packet.getAddress().getHostAddress();
                        int remoteSyncPort = Integer.parseInt(parts[3]);

                        if (downloadDatabase(remoteIp, remoteSyncPort)) {
                            applyDatabaseUpdate();
                        }
                    }
                }
                else if (msg.startsWith("UPDATE_DB")) {
                    // Protocolo: UPDATE_DB;PORT;SQL
                    // Split em 3 para preservar o SQL (caso tenha ;)
                    String[] parts = msg.split(";", 3);

                    if (parts.length == 3) {
                        int senderPort = Integer.parseInt(parts[1]);

                        // FIX: Só executa se não fui eu que enviei
                        if (senderPort != myPort) {
                            db.executeSyncUpdate(parts[2]);
                        }
                    }
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private boolean downloadDatabase(String host, int port) {
        String tempPath = dbPath + ".temp";
        try (Socket socket = new Socket(host, port);
             InputStream is = socket.getInputStream();
             FileOutputStream fos = new FileOutputStream(tempPath)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) fos.write(buffer, 0, bytesRead);

            System.out.println("[Sync] Download do ficheiro concluído.");
            return true;
        } catch (IOException e) {
            System.out.println("[Sync] Erro download: " + e.getMessage());
            return false;
        }
    }

    private void applyDatabaseUpdate() {
        db.close();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        try {
            Path source = Paths.get(dbPath + ".temp");
            Path target = Paths.get(dbPath);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[Sync] Base de dados substituída com sucesso.");
        } catch (IOException e) {
            System.out.println("[Sync] Erro crítico ao substituir BD: " + e.getMessage());
        } finally {
            db.connect();
            System.out.println("[Sync] Nova versão carregada: v" + db.getVersao());
        }
    }
}