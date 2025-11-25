package server;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final Database db;

    // Estado da Sessão
    private int currentUserId = -1;
    private String currentUserType = ""; // "DOCENTE" ou "ESTUDANTE"
    private String currentUserName = "";

    public ClientHandler(Socket clientSocket, Database db) {
        this.clientSocket = clientSocket;
        this.db = db;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            out.println("BEM-VINDO;Sistema de Perguntas (V2). Comandos: LOGIN, REGISTER, CREATE");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[Servidor] Recebido de " + currentUserName + ": " + line);
                String[] parts = line.split(";");
                String comando = parts[0].toUpperCase();

                switch (comando) {
                    case "REGISTER":
                        if (parts.length < 5) {
                            out.println("ERRO;Sintaxe: REGISTER;TIPO;...");
                            break;
                        }
                        String tipo = parts[1].toUpperCase();
                        boolean sucesso = false;

                        if (tipo.equals("DOCENTE")) {
                            sucesso = db.registerDocente(parts[2], parts[3], parts[4]);
                        } else if (tipo.equals("ESTUDANTE") && parts.length >= 6) {
                            try {
                                int num = Integer.parseInt(parts[2]);
                                sucesso = db.registerEstudante(num, parts[3], parts[4], parts[5]);
                            } catch (NumberFormatException e) {
                                out.println("ERRO;Numero estudante invalido.");
                                break;
                            }
                        }
                        if (sucesso) out.println("SUCESSO;Registo efetuado.");
                        else out.println("ERRO;Falha no registo.");
                        break;

                    case "LOGIN":
                        if (parts.length < 3) {
                            out.println("ERRO;Sintaxe: LOGIN;email;pass");
                            break;
                        }
                        String userInfo = db.authenticateUser(parts[1], parts[2]);
                        // userInfo formato: "DOCENTE;1;Nome" ou "ESTUDANTE;2;Nome;Numero"
                        if (userInfo != null) {
                            String[] userParts = userInfo.split(";");
                            this.currentUserType = userParts[0];
                            this.currentUserId = Integer.parseInt(userParts[1]);
                            this.currentUserName = userParts[2];

                            out.println("SUCESSO;Bem-vindo " + currentUserName + " (" + currentUserType + ")");
                        } else {
                            out.println("ERRO;Credenciais invalidas.");
                        }
                        break;

                    case "CREATE":
                        // Apenas docentes podem criar perguntas
                        if (!currentUserType.equals("DOCENTE")) {
                            out.println("ERRO;Apenas docentes podem criar perguntas.");
                            break;
                        }
                        // Formato esperado: CREATE;Enunciado;OpcaoCerta(A/B..);DataIni;DataFim;Op1;Op2;Op3...
                        if (parts.length < 7) {
                            out.println("ERRO;Dados insuficientes. CREATE;Enunciado;OpCerta;Ini;Fim;Op1;Op2...");
                            break;
                        }

                        String enunciado = parts[1];
                        String opCerta = parts[2]; // Ex: "A"
                        String inicio = parts[3];
                        String fim = parts[4];

                        // Recolher todas as opções (do índice 5 até ao fim)
                        List<String> opcoes = new ArrayList<>();
                        for (int i = 5; i < parts.length; i++) {
                            opcoes.add(parts[i]);
                        }

                        String codigoGerado = db.createPergunta(currentUserId, enunciado, opCerta, inicio, fim, opcoes);

                        if (codigoGerado != null) {
                            out.println("SUCESSO;Pergunta criada com codigo: " + codigoGerado);
                        } else {
                            out.println("ERRO;Falha ao criar pergunta na BD.");
                        }
                        break;

                    default:
                        out.println("ERRO;Comando desconhecido.");
                }
            }
        } catch (IOException e) {
            System.out.println("[Servidor] Cliente " + currentUserName + " desconectado.");
        }
    }
}