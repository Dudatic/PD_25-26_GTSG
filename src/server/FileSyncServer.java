package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class FileSyncServer implements Runnable {
    private final int port;
    private final String dbPath;

    public FileSyncServer(int port, String dbPath) {
        this.port = port;
        this.dbPath = dbPath;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[Sync] Servidor de Ficheiros ativo na porta " + port);
            while (true) {
                try (Socket socket = serverSocket.accept();
                     FileInputStream fis = new FileInputStream(dbPath);
                     OutputStream os = socket.getOutputStream()) {

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) os.write(buffer, 0, bytesRead);
                    os.flush();
                } catch (IOException e) { System.out.println("[Sync] Erro envio: " + e.getMessage()); }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }
}