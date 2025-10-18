package server;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            out.println("[Servidor] Conexão estabelecida com sucesso!");
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[Servidor] Recebido: " + line);
                out.println("Eco: " + line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
