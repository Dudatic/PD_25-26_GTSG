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
            byte[] buffer = new byte[4096];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());
                String[] parts = msg.split(";");
                String comando = parts[0];

                if (comando.equals("HEARTBEAT")) {
                    int remoteTcpPort = Integer.parseInt(parts[2]);
                    if (remoteTcpPort == myPort) continue;

                    int remoteVersion = Integer.parseInt(parts[1]);
                    int localVersion = db.getVersao();

                    // Só sincroniza se a versão remota for MAIOR
                    if (remoteVersion > localVersion) {
                        System.out.println("[Multicast] Versão remota (v" + remoteVersion + ") detetada. A atualizar...");

                        String remoteIp = packet.getAddress().getHostAddress();
                        int remoteSyncPort = Integer.parseInt(parts[3]);

                        // 1. Download para .temp
                        if (downloadDatabase(remoteIp, remoteSyncPort)) {
                            // 2. Aplicar a atualização (Swap)
                            applyDatabaseUpdate();
                        }
                    }
                }
                if (comando.equals("UPDATE_DB") && parts.length > 1) db.executeSyncUpdate(parts[1]);
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

            System.out.println("[Sync] Download concluído com sucesso.");
            return true;
        } catch (IOException e) {
            System.out.println("[Sync] Erro download: " + e.getMessage());
            return false;
        }
    }

    private void applyDatabaseUpdate() {
        try {
            // 1. Fechar conexão atual (obrigatório no Windows para libertar o ficheiro)
            db.close();

            // 2. Substituir o ficheiro .db pelo .db.temp
            Path source = Paths.get(dbPath + ".temp");
            Path target = Paths.get(dbPath);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[Sync] Base de dados substituída com sucesso.");

            // 3. Reabrir conexão
            db.connect();
            System.out.println("[Sync] Nova versão carregada: v" + db.getVersao());

        } catch (IOException e) {
            System.out.println("[Sync] Erro crítico ao substituir BD: " + e.getMessage());
            // Tenta reconectar caso falhe a cópia
            db.connect();
        }
    }
}