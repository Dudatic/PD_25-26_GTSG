package server;

import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;

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
            byte[] buffer = new byte[8192]; // Buffer maior para queries longas

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), 0, packet.getLength());
                // Usamos split com limite 3 para garantir que se a SQL tiver ";" não parta mal
                String[] parts = msg.split(";", 3);
                String comando = parts[0];

                if (comando.equals("HEARTBEAT")) {
                    // Formato: HEARTBEAT;versao_db;porto_clientes;porto_sincronizacao
                    int remoteTcpPort = Integer.parseInt(parts[2]);

                    // Ignorar mensagens minhas
                    if (remoteTcpPort == myPort) continue;

                    int remoteVersion = Integer.parseInt(parts[1]);
                    int localVersion = db.getVersao();

                    // Se a versão remota for muito superior (mais que +1), perdi pacotes -> DOWNLOAD TOTAL
                    if (remoteVersion > localVersion + 1) {
                        System.out.println("[Multicast] Detetada desincronização grave (v" + remoteVersion + " vs v" + localVersion + "). A iniciar download total...");

                        String remoteIp = packet.getAddress().getHostAddress();
                        int remoteSyncPort = Integer.parseInt(parts[3]);

                        downloadDatabase(remoteIp, remoteSyncPort);
                    }
                }

                if (comando.equals("UPDATE_DB")) {
                    // Formato: UPDATE_DB;versao;sql
                    if (parts.length == 3) {
                        int remoteVersion = Integer.parseInt(parts[1]);
                        String sqlQuery = parts[2];
                        int localVersion = db.getVersao();

                        // Só aplico se for EXATAMENTE a próxima versão
                        if (remoteVersion == localVersion + 1) {
                            System.out.println("[Multicast] Update incremental recebido (v" + remoteVersion + ").");
                            db.executeSyncUpdate(sqlQuery);
                        }
                        // Se for menor ou igual, é repetido, ignora.
                        // Se for maior, o Heartbeat vai tratar de fazer o download total depois.
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void downloadDatabase(String host, int port) {
        String tempPath = dbPath + ".temp";

        System.out.println("[Sync] A ligar a " + host + ":" + port + " para sacar DB...");

        try (Socket socket = new Socket(host, port);
             InputStream is = socket.getInputStream();
             FileOutputStream fos = new FileOutputStream(tempPath)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }

            System.out.println("[Sync] Download concluído. A substituir ficheiro...");

            // 1. Desconectar a BD atual para libertar o ficheiro
            db.disconnect();

            // 2. Substituir o ficheiro
            File original = new File(dbPath);
            File temp = new File(tempPath);

            if (original.exists() && !original.delete()) {
                System.out.println("[Sync] ERRO: Não foi possível apagar a BD antiga.");
            }

            if (!temp.renameTo(original)) {
                System.out.println("[Sync] ERRO: Não foi possível renomear a BD nova.");
            }

            // 3. Reconectar
            db.reconnect();
            System.out.println("[Sync] Base de dados sincronizada com sucesso! Nova versão: " + db.getVersao());

        } catch (IOException e) {
            System.out.println("[Sync] Erro na transferência: " + e.getMessage());
            // Em caso de erro, garante que voltamos a ligar à BD antiga se ela existir
            db.reconnect();
        }
    }
}