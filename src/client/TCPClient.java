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
    // MUDANÇA: Adicionado "throws IOException" e removido o "catch" final
    public void start() throws IOException {
        try (Socket socket = new Socket(serverIP, serverPort);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner sc = new Scanner(System.in)) {

            System.out.println("[Cliente] Ligado ao servidor " + serverIP + ":" + serverPort);

            // Lê a mensagem de boas vindas
            if (in.ready()) {
                System.out.println("[Servidor diz] " + in.readLine());
            }

            while (true) {
                System.out.print("> ");
                if (!sc.hasNextLine()) break;
                String msg = sc.nextLine();
                out.println(msg);

                if (out.checkError()) throw new IOException("Erro no envio.");

                String resposta = in.readLine();
                if (resposta == null) throw new IOException("Conexão fechada.");

                System.out.println("[Servidor] " + resposta);
            }
        }
        // O catch foi removido propositadamente para o erro subir ao ClientMain!
    }
}
