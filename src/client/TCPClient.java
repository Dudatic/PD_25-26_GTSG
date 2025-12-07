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

        // Thread 1: Escuta o Servidor (Output)
        // Esta thread corre em paralelo e imprime tudo o que o servidor mandar
        new Thread(new ServerListener()).start();

        // Thread 2 (Main): Lê do Teclado (Input)
        // Esta thread fica bloqueada à espera que o utilizador escreva
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        Scanner sc = new Scanner(System.in);

        while (running) {
            // Verifica se há input no teclado
            if (sc.hasNextLine()) {
                String msg = sc.nextLine();
                out.println(msg);
            } else {
                break; // Sai se o scanner fechar (ex: Ctrl+D)
            }
        }
        close();
    }

    private void close() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            // Ignorar erro no fecho
        }
    }

    // Classe interna para ouvir o servidor sem bloquear a escrita
    private class ServerListener implements Runnable {
        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String msg;
                while ((msg = in.readLine()) != null) {
                    // --- ALTERAÇÃO AQUI ---
                    if (msg.startsWith("SUCESSO_CSV;")) {
                        System.out.println("[Cliente] A receber ficheiro CSV...");

                        // 1. Tira o prefixo e repõe as quebras de linha reais
                        String conteudo = msg.substring(12).replace("@@NWL@@", "\n");

                        try (FileWriter fw = new FileWriter("relatorio_pergunta.csv")) {
                            fw.write(conteudo);
                            System.out.println("[Cliente] Ficheiro 'relatorio_pergunta.csv' gravado com sucesso!");
                        } catch (IOException e) {
                            System.out.println("[Cliente] Erro ao gravar ficheiro: " + e.getMessage());
                        }
                    } else {
                        // Mensagem normal
                        System.out.println("[Servidor]: " + msg);
                        System.out.print("> ");
                    }
                }
            } catch (IOException e) {
                if (running) {
                    System.out.println("\n[Cliente] Ligação ao servidor perdida.");
                    System.exit(0);
                }
            }
        }
    }

    //    private class ServerListener implements Runnable {
//        @Override
//        public void run() {
//            try {
//                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//                String msg;
//                while ((msg = in.readLine()) != null) {
//                    System.out.println("[Servidor]: " + msg);
//                    System.out.print("> "); // Mostra o prompt novamente para ficar bonito
//                }
//            } catch (IOException e) {
//                if (running) {
//                    System.out.println("\n[Cliente] Ligação ao servidor perdida.");
//                    System.exit(0);
//                }
//            }
//        }
//    }
}