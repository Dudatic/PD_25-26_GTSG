package server;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final Database db; // <--- NOVO

    public ClientHandler(Socket clientSocket, Database db) {
        this.clientSocket = clientSocket;
        this.db = db;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            out.println("BEM-VINDO;Sistema de Perguntas Distribuido");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[Servidor] Recebido: " + line);

                String[] parts = line.split(";");
                String comando = parts[0].toUpperCase();

                switch (comando) {
                    case "LOGIN":
                        out.println("ERRO;Login nao implementado ainda");
                        break;
                    case "REGISTER":
                        out.println("ERRO;Registo nao implementado ainda");
                        break;
                    default:
                        out.println("ERRO;Comando desconhecido");
                }
            }
        } catch (IOException e) {
            System.out.println("[Servidor] Cliente desconectado.");
        }
    }
}