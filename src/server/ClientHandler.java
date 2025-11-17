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

            out.println("BEM-VINDO; Sistema de Perguntas Distribuido"); // Mensagem de boas-vindas

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[Servidor] Recebido: " + line);

                // Dividir a mensagem por ponto e vírgula (ex: "LOGIN;user;pass")
                String[] parts = line.split(";");
                String comando = parts[0].toUpperCase();

                switch (comando) {
                    case "LOGIN":
                        // Futuro: Verificar BD
                        out.println("ERRO;Login nao implementado ainda");
                        break;

                    case "REGISTER":
                        // Futuro: Inserir na BD
                        out.println("ERRO;Registo nao implementado ainda");
                        break;

                    default:
                        out.println("ERRO;Comando desconhecido: " + comando);
                        break;
                }
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
