package server;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final Database db;
    private final ServerTCP server;

    private int currentUserId = -1;
    private String currentUserType = "";
    private String currentUserName = "";

    public ClientHandler(Socket clientSocket, Database db, ServerTCP server) {
        this.clientSocket = clientSocket;
        this.db = db;
        this.server = server;
    }

    @Override
    public void run() {
        System.out.println("[Servidor] Novo cliente conectado: " + clientSocket.getInetAddress());

        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            server.addClient(out);
            out.println("BEM-VINDO;Sistema de Perguntas (V2). Comandos: REGISTER, LOGIN, LOGOUT, EDIT_PROFILE, CREATE, EDIT_QUESTION, LIST, CSV, DELETE, GET_QUESTION, ANSWER, MY_GRADES");

            String line;
            while ((line = in.readLine()) != null) {
                // Log removido para limpar a consola

                String[] parts = line.split(";");
                String comando = parts[0].toUpperCase();

                switch (comando) {
                    case "REGISTER":
                        if (parts.length < 2) { out.println("ERRO;Sintaxe invalida."); break; }
                        String tipo = parts[1].toUpperCase();
                        boolean sucesso = false;

                        if (tipo.equals("DOCENTE")) {
                            if (parts.length < 6) { out.println("ERRO;Dados insuficientes."); break; }
                            if (!db.checkDocenteCode(parts[5])) { out.println("ERRO;Codigo de docente invalido."); break; }
                            sucesso = db.registerDocente(parts[2], parts[3], parts[4]);
                        } else if (tipo.equals("ESTUDANTE")) {
                            if (parts.length < 6) { out.println("ERRO;Dados insuficientes."); break; }
                            try {
                                int num = Integer.parseInt(parts[2]);
                                sucesso = db.registerEstudante(num, parts[3], parts[4], parts[5]);
                            } catch (NumberFormatException e) { out.println("ERRO;Numero invalido."); break; }
                        } else { out.println("ERRO;Tipo invalido."); break; }

                        if (sucesso) out.println("SUCESSO;Registo efetuado.");
                        else out.println("ERRO;Falha no registo (Dados duplicados?).");
                        break;

                    case "LOGIN":
                        if (parts.length < 3) { out.println("ERRO;Sintaxe: LOGIN;email;pass"); break; }
                        String userInfo = db.authenticateUser(parts[1], parts[2]);
                        if (userInfo != null) {
                            String[] userParts = userInfo.split(";");
                            this.currentUserType = userParts[0];
                            this.currentUserId = Integer.parseInt(userParts[1]);
                            this.currentUserName = userParts[2];
                            out.println("SUCESSO;Bem-vindo " + currentUserName);
                        } else { out.println("ERRO;Credenciais invalidas."); }
                        break;

                    case "LOGOUT":
                        if (currentUserId == -1) {
                            out.println("ERRO;Nao existe sessao ativa.");
                        } else {
                            // Limpa os dados da sessão atual
                            this.currentUserId = -1;
                            this.currentUserType = "";
                            this.currentUserName = "";
                            out.println("SUCESSO;Sessao terminada. Ate logo!");
                        }
                        break;

                    case "EDIT_PROFILE":
                        if (currentUserId == -1) { out.println("ERRO;Login necessario."); break; }
                        if (parts.length < 4) { out.println("ERRO;Sintaxe: EDIT_PROFILE;nome;email;pass"); break; }
                        boolean editOK = false;
                        if (currentUserType.equals("DOCENTE")) editOK = db.updateDocente(currentUserId, parts[1], parts[2], parts[3]);
                        else editOK = db.updateEstudante(currentUserId, parts[1], parts[2], parts[3]);

                        if (editOK) { this.currentUserName = parts[1]; out.println("SUCESSO;Perfil atualizado."); }
                        else out.println("ERRO;Falha ao atualizar.");
                        break;

                    case "CREATE":
                        if (!currentUserType.equals("DOCENTE")) { out.println("ERRO;Apenas docentes."); break; }
                        if (parts.length < 7) { out.println("ERRO;Dados insuficientes."); break; }
                        List<String> opcoes = new ArrayList<>();
                        for (int i = 5; i < parts.length; i++) opcoes.add(parts[i]);

                        String cod = db.createPergunta(currentUserId, parts[1], parts[2], parts[3], parts[4], opcoes);
                        if (cod != null) {
                            out.println("SUCESSO;Pergunta criada: " + cod);
                            server.broadcast("Nova pergunta disponivel: " + parts[1] + " (" + cod + ")");
                        } else out.println("ERRO;Falha ao criar.");
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

                    case "LIST":
                    case "CSV":
                        if (!currentUserType.equals("DOCENTE")) { out.println("ERRO;Apenas docentes."); break; }
                        if (comando.equals("LIST")) {
                            String l = db.getPerguntasDocente(currentUserId);
                            out.println("SUCESSO;Perguntas:\n" + l.replace("#", "\n"));
                        } else {
                            if (parts.length < 2) { out.println("ERRO;Indique o codigo."); break; }
                            String csv = db.getRespostasCSV(currentUserId, parts[1]);
                            if (csv != null) out.println("SUCESSO;CSV:\n" + csv);
                            else out.println("ERRO;Falha ao gerar CSV.");
                        }
                        break;

                    case "GET_QUESTION":
                        if (parts.length < 2) { out.println("ERRO;Indique o codigo."); break; }
                        String txt = db.getPerguntaTexto(parts[1]);
                        if (txt != null) out.println("SUCESSO;" + txt);
                        else out.println("ERRO;Nao encontrada.");
                        break;

                    case "ANSWER":
                        if (!currentUserType.equals("ESTUDANTE")) { out.println("ERRO;Apenas estudantes."); break; }
                        if (parts.length < 3) { out.println("ERRO;Sintaxe: ANSWER;codigo;opcao"); break; }
                        if (!db.isPerguntaAtiva(parts[1])) { out.println("ERRO;Pergunta inativa ou inexistente."); break; }
                        if (db.submitAnswer(currentUserId, parts[1], parts[2])) out.println("SUCESSO;Resposta registada.");
                        else out.println("ERRO;Falha (Ja respondeu?).");
                        break;

                    case "MY_GRADES":
                        if (!currentUserType.equals("ESTUDANTE")) { out.println("ERRO;Apenas estudantes."); break; }
                        String hist = db.getHistoricoEstudante(currentUserId);
                        out.println("SUCESSO;Historico:\n" + hist.replace("#", "\n"));
                        break;

                    default:
                        out.println("ERRO;Comando desconhecido.");
                }
            }
            server.removeClient(out);
        } catch (IOException e) {
            System.out.println("[Servidor] Cliente " + (currentUserName.isEmpty() ? clientSocket.getInetAddress() : currentUserName) + " desconectado.");
        }
    }
}