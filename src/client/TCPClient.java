package client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class TCPClient {
    private final String serverIP;
    private final int serverPort;
    private Socket socket;
    private boolean running = true;

    public TCPClient(String serverIP, int serverPort) {
        this.serverIP = serverIP;
        this.serverPort = serverPort;
    }

    public void start() throws IOException {
        socket = new Socket(serverIP, serverPort);
        System.out.println("[Cliente] Ligado ao servidor " + serverIP + ":" + serverPort);

        new Thread(new ServerListener()).start();

        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        Scanner sc = new Scanner(System.in);

        while (running) {
            if (sc.hasNextLine()) {
                String msg = sc.nextLine();
                out.println(msg);
            } else break;
        }
        close();
    }

    private void close() {
        running = false;
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException e) {}
    }

    private class ServerListener implements Runnable {
        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String msg;
                while ((msg = in.readLine()) != null) {
                    System.out.println("[Servidor]: " + msg);
                    System.out.print("> ");
                }
            } catch (IOException e) {
                if (running) {
                    System.out.println("\n[Cliente] Ligação perdida.");
                    System.exit(0);
                }
            }
        }
    }
}