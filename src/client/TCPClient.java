package client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

//Liga-se ao servidor via TCP e gere a interação na consola

public class TCPClient {
    private final String serverIP;
    private final int serverPort;

    public TCPClient(String serverIP, int serverPort) {
        this.serverIP = serverIP;
        this.serverPort = serverPort;
    }


    // ADICIONADO: "throws IOException" para avisar o ClientMain em caso de falha
    // ADICIONADO: "throws IOException" para avisar o ClientMain em caso de falha
    public void start() throws IOException {
        // REMOVIDO: O try-catch envolvente. Mantemos apenas o try-with-resources.
        try (Socket socket = new Socket(serverIP, serverPort);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner sc = new Scanner(System.in)) {

            System.out.println("[Cliente] Ligado ao servidor " + serverIP + ":" + serverPort);

            // Leitura inicial (se o servidor mandar boas-vindas)
            if (in.ready()) {
                System.out.println("[Servidor diz] " + in.readLine());
            }

            while (true) {
                System.out.print("> ");
                if (!sc.hasNextLine()) break;
                String msg = sc.nextLine();

                out.println(msg);

                // ADICIONADO: Verificação de erro no envio
                if (out.checkError()) {
                    throw new IOException("Erro ao enviar dados (servidor pode ter caído).");
                }

                String resposta = in.readLine();
                // ADICIONADO: Se a resposta for null, a ligação caiu
                if (resposta == null) {
                    throw new IOException("O servidor encerrou a ligação.");
                }
                System.out.println("[Servidor] " + resposta);
            }
        }
    }
}
