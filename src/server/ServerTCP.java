package server;
import java.net.*;
import java.io.*;

public class ServerTCP {
    private final int tcpPort;
    private final String diretoriaIP;

    public ServerTCP(int tcpPort, String diretoriaIP) {
        this.tcpPort = tcpPort;
        this.diretoriaIP = diretoriaIP;
    }

    public void start() {
        System.out.println("[Servidor] Iniciado no porto TCP " + tcpPort);
        new Thread(new UDPRegister(diretoriaIP, tcpPort)).start();

        try (ServerSocket serverSocket = new ServerSocket(tcpPort)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
