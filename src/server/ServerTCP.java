package server;
import java.net.*;
import java.io.*;

public class ServerTCP {
    private final int tcpPort;
    private final String diretoriaIP;
    private final Database db;

    public ServerTCP(int tcpPort, String diretoriaIP, Database db) {
        this.tcpPort = tcpPort;
        this.diretoriaIP = diretoriaIP;
        this.db = db;
    }

    public void start() {
        System.out.println("[Servidor] Iniciado no porto TCP " + tcpPort);
        new Thread(new UDPRegister(diretoriaIP, tcpPort)).start();

        try (ServerSocket serverSocket = new ServerSocket(tcpPort)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket, db)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
