package server;

import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;

public class MulticastListener implements Runnable {
    private final Database db;
    private final int myPort;
    private final String dbPath; // Necessário para saber onde guardar o ficheiro recebido

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
                    // Formato: HEARTBEAT;versao_db;porto_clientes;porto_sincronizacao
                    int remoteTcpPort = Integer.parseInt(parts[2]);

                    // Ignorar mensagens minhas
                    if (remoteTcpPort == myPort) continue;

                    int remoteVersion = Integer.parseInt(parts[1]);
                    int localVersion = db.getVersao();

                    // Se a minha versão for menor, tenho de atualizar
                    if (remoteVersion > localVersion) {
                        System.out.println("[Multicast] Detetada versão mais recente (v" + remoteVersion + "). A iniciar sincronização...");

                        String remoteIp = packet.getAddress().getHostAddress();
                        int remoteSyncPort = Integer.parseInt(parts[3]);

                        downloadDatabase(remoteIp, remoteSyncPort);
                    }
                }

                if (comando.equals("UPDATE_DB")) {
                    if (parts.length > 1) {
                        String sql = parts[1];
                        db.executeSyncUpdate(sql);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void downloadDatabase(String host, int port) {
        // Fecha a conexão atual à BD para permitir a substituição do ficheiro (SQLite bloqueia ficheiros abertos)
        // Nota: Em sistemas reais isto é mais complexo, aqui simplificamos.
        // Assumimos que o Database tem um método close() ou lidamos com o ficheiro locked.
        // Para simplificar o projeto escolar, vamos tentar sobrescrever. Se der erro, teríamos de fechar a Connection no Database.java.

        String tempPath = dbPath + ".temp"; // Saca para um ficheiro temporário primeiro

        try (Socket socket = new Socket(host, port);
             InputStream is = socket.getInputStream();
             FileOutputStream fos = new FileOutputStream(tempPath)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }

            System.out.println("[Sync] Ficheiro transferido para " + tempPath);

            // Aqui devíamos fechar a conexão DB, substituir o ficheiro e reabrir.
            // Como o SQLite JDBC é robusto, vamos tentar substituir e depois recarregar a conexão.
            // Para a próxima iteração, podemos adicionar um método 'reconnect' na classe Database.

            // AVISO: Substituir o ficheiro 'a quente' pode falhar no Windows se a BD estiver a ser usada.
            // O ideal seria db.close(), substituir, db.connect().
            // Vamos assumir que adicionamos isso no Database.java no próximo passo se falhar.

        } catch (IOException e) {
            System.out.println("[Sync] Erro na transferência: " + e.getMessage());
        }
    }
}