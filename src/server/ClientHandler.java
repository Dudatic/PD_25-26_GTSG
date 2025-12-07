package server;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
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

            out.println("BEM-VINDO;Sistema de Perguntas (V2). Comandos: LOGIN, REGISTER, CREATE, LIST, ANSWER, CSV");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[Servidor] Recebido de " + (currentUserName.isEmpty() ? "Anonimo" : currentUserName) + ": " + line);
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

                    case "LIST":
                        if (currentUserId == -1) {
                            out.println("ERRO;Precisa de fazer login primeiro.");
                            break;
                        }

                        List<String> perguntas;
                        // Verifica o tipo de utilizador e chama o método correto da DB
                        if (currentUserType.equals("DOCENTE")) {
                            perguntas = db.getPerguntasDocente(currentUserId);
                        } else {
                            perguntas = db.getPerguntasEstudante(currentUserId);
                        }

                        if (perguntas.isEmpty()) {
                            out.println("INFO;Nenhuma pergunta encontrada (ou nenhuma ativa).");
                        } else {
                            out.println("LISTA_INICIO;--- Perguntas Disponiveis ---");
                            for (String p : perguntas) {
                                out.println(p);
                            }
                            out.println("LISTA_FIM");
                        }
                        break;


                    case "ANSWER":
                        // Sintaxe: ANSWER;id_pergunta;opcao
                        if (!currentUserType.equals("ESTUDANTE")) {
                            out.println("ERRO;Apenas estudantes podem responder.");
                            break;
                        }
                        if (parts.length < 3) {
                            out.println("ERRO;Sintaxe: ANSWER;id_pergunta;opcao");
                            break;
                        }
                        try {
                            int pId = Integer.parseInt(parts[1]);
                            String opcao = parts[2].toUpperCase();

                            String resultado = db.submitAnswer(currentUserId, pId, opcao);
                            out.println(resultado);

                        } catch (NumberFormatException e) {
                            out.println("ERRO;ID da pergunta invalido.");
                        }
                        break;

                    case "CSV":
                        // Sintaxe: CSV;id_pergunta
                        if (!currentUserType.equals("DOCENTE")) {
                            out.println("ERRO;Apenas docentes podem exportar dados.");
                            break;
                        }
                        if (parts.length < 2) {
                            out.println("ERRO;Sintaxe: CSV;id_pergunta");
                            break;
                        }
                        try {
                            int pId = Integer.parseInt(parts[1]);
                            String respostaCsv = db.getRelatorioCSV(pId, currentUserId);

                            // Se o CSV for grande, o cliente terá de ler várias linhas.
                            // Aqui enviamos tudo numa string (com \n)
                            out.println(respostaCsv);

                        } catch (NumberFormatException e) {
                            out.println("ERRO;ID invalido.");
                        }
                        break;

                    case "EDIT_QUESTION":
                        if (!currentUserType.equals("DOCENTE")) { out.println("ERRO;Apenas docentes."); break; }
                        if (parts.length < 8) { out.println("ERRO;Dados insuficientes."); break; }
                        List<String> nOpcoes = new ArrayList<>();
                        for (int i = 6; i < parts.length; i++) nOpcoes.add(parts[i]);

                        if (db.updatePergunta(currentUserId, parts[1], parts[2], parts[3], parts[4], parts[5], nOpcoes))
                            out.println("SUCESSO;Pergunta atualizada.");
                        else out.println("ERRO;Falha (Tem respostas? Nao e o autor?).");
                        break;

                    case "DELETE":
                        if (!currentUserType.equals("DOCENTE")) { out.println("ERRO;Apenas docentes."); break; }
                        if (parts.length < 2) { out.println("ERRO;Indique o codigo."); break; }
                        if (db.deletePergunta(currentUserId, parts[1])) out.println("SUCESSO;Pergunta apagada.");
                        else out.println("ERRO;Falha (Tem respostas? Nao e o autor?).");
                        break;

                    default:
                        out.println("ERRO;Comando desconhecido (" + comando + ")");
                        break;
                }
            }
        } catch (IOException e) {
            System.out.println("[Servidor] Cliente " + (currentUserName.isEmpty() ? "Anonimo" : currentUserName) + " desconectado.");
        }
    }
}