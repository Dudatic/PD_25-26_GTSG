package server;

import java.sql.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Database {
    private final String dbPath;
    private Connection connection;
    private UDPRegister udpRegister;

    public Database(String dbPath) {
        this.dbPath = dbPath;
    }

    public void setUdpRegister(UDPRegister udpRegister) {
        this.udpRegister = udpRegister;
    }

    public void connect() {
        try {
            File dbFile = new File(dbPath);
            if (dbFile.getParentFile() != null) {
                dbFile.getParentFile().mkdirs();
            }

            String url = "jdbc:sqlite:" + dbPath;
            connection = DriverManager.getConnection(url);
            System.out.println("[BD] Conexão a SQLite estabelecida: " + dbPath);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }

            createTables();

        } catch (SQLException e) {
            System.out.println("[BD] Erro crítico ao conectar: " + e.getMessage());
        }
    }

    // --- Métodos para permitir substituição do ficheiro .db ---
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[BD] Desconectado (para permitir sync).");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void reconnect() {
        connect();
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS docente (id INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT NOT NULL, email TEXT UNIQUE NOT NULL, password TEXT NOT NULL);");
            stmt.execute("CREATE TABLE IF NOT EXISTS estudante (id INTEGER PRIMARY KEY AUTOINCREMENT, numero INTEGER UNIQUE NOT NULL, nome TEXT NOT NULL, email TEXT UNIQUE NOT NULL, password TEXT NOT NULL);");
            stmt.execute("CREATE TABLE IF NOT EXISTS pergunta (id INTEGER PRIMARY KEY AUTOINCREMENT, docente_id INTEGER, enunciado TEXT NOT NULL, opcao_certa TEXT NOT NULL, data_inicio TEXT, data_fim TEXT, codigo TEXT UNIQUE, FOREIGN KEY(docente_id) REFERENCES docente(id));");
            stmt.execute("CREATE TABLE IF NOT EXISTS opcao (id INTEGER PRIMARY KEY AUTOINCREMENT, pergunta_id INTEGER, codigo TEXT NOT NULL, texto TEXT NOT NULL, FOREIGN KEY(pergunta_id) REFERENCES pergunta(id) ON DELETE CASCADE);");
            stmt.execute("CREATE TABLE IF NOT EXISTS resposta (id INTEGER PRIMARY KEY AUTOINCREMENT, pergunta_id INTEGER, estudante_id INTEGER, resposta_dada TEXT NOT NULL, data_submissao TEXT, FOREIGN KEY(pergunta_id) REFERENCES pergunta(id), FOREIGN KEY(estudante_id) REFERENCES estudante(id));");
            stmt.execute("CREATE TABLE IF NOT EXISTS configuracao (id INTEGER PRIMARY KEY CHECK (id = 1), versao_bd INTEGER DEFAULT 0, codigo_registo_docente TEXT DEFAULT 'admin123');");
            stmt.execute("INSERT OR IGNORE INTO configuracao (id, versao_bd, codigo_registo_docente) VALUES (1, 0, 'admin123');");
        }
    }

    // --- MÉTODOS DE LÓGICA DE NEGÓCIO ---

    public synchronized boolean registerDocente(String nome, String email, String password) {
        String sql = "INSERT INTO docente(nome, email, password) VALUES(?,?,?)";
        return executeInsert(sql, nome, email, password);
    }

    public synchronized boolean registerEstudante(int numero, String nome, String email, String password) {
        String sql = "INSERT INTO estudante(numero, nome, email, password) VALUES(?,?,?,?)";
        return executeInsert(sql, numero, nome, email, password);
    }

    // Método principal para criar uma pergunta completa
    public synchronized String createPergunta(int docenteId, String enunciado, String opcaoCerta, String dataInicio, String dataFim, List<String> opcoes) {
        String codigoPergunta = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        String sqlPergunta = "INSERT INTO pergunta(docente_id, enunciado, opcao_certa, data_inicio, data_fim, codigo) VALUES(?,?,?,?,?,?)";
        String sqlOpcao = "INSERT INTO opcao(pergunta_id, codigo, texto) VALUES(?,?,?)";

        try {
            connection.setAutoCommit(false); // Iniciar transação

            // 1. Inserir a Pergunta
            int perguntaId = -1;
            try (PreparedStatement pstmt = connection.prepareStatement(sqlPergunta, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, docenteId);
                pstmt.setString(2, enunciado);
                pstmt.setString(3, opcaoCerta);
                pstmt.setString(4, dataInicio);
                pstmt.setString(5, dataFim);
                pstmt.setString(6, codigoPergunta);
                pstmt.executeUpdate();

                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    perguntaId = rs.getInt(1);
                }
            }

            if (perguntaId == -1) {
                connection.rollback();
                return null;
            }

            // 2. Inserir as Opções
            char codigoOpcao = 'A';
            try (PreparedStatement pstmtOp = connection.prepareStatement(sqlOpcao)) {
                for (String textoOpcao : opcoes) {
                    pstmtOp.setInt(1, perguntaId);
                    pstmtOp.setString(2, String.valueOf(codigoOpcao));
                    pstmtOp.setString(3, textoOpcao);
                    pstmtOp.addBatch();
                    codigoOpcao++;
                }
                pstmtOp.executeBatch();
            }

            connection.commit();
            incrementarVersaoDB();

            // --- NOTIFICAR CLUSTER DA TRANSAÇÃO COMPLETA ---
            // Como createPergunta é complexo, vamos reconstruir as SQLs para envio
            if (udpRegister != null) {
                // Nota: Num sistema real, isto seria enviado como um bloco transacional ou comando lógico.
                // Aqui vamos simplificar e enviar as queries cruas com os valores.

                // Query da Pergunta
                String updatePergunta = "INSERT INTO pergunta(docente_id, enunciado, opcao_certa, data_inicio, data_fim, codigo) " +
                        "VALUES(" + docenteId + ", '" + enunciado + "', '" + opcaoCerta + "', '" + dataInicio + "', '" + dataFim + "', '" + codigoPergunta + "')";
                udpRegister.sendSyncUpdate(getVersao(), updatePergunta);

                // Queries das Opções (Para simplificar a sync, enviamos uma a uma, embora a versão seja a mesma...
                // O ideal seria o backup sacar a versão só no fim, mas vamos enviar queries individuais)
                // Pequeno ajuste: a versão só mudou uma vez.
                // Correção de estratégia para TP: Enviar queries individuais pode ser arriscado se perder pacotes,
                // mas vamos assumir que funciona para a demo.

                // O ID da pergunta no outro servidor pode ser diferente se não estiverem sincronizados.
                // SOLUÇÃO ROBUSTA: Usar uma subquery para buscar o ID pelo código único.
                String subQueryId = "(SELECT id FROM pergunta WHERE codigo='" + codigoPergunta + "')";

                char c = 'A';
                for (String texto : opcoes) {
                    String updateOpcao = "INSERT INTO opcao(pergunta_id, codigo, texto) VALUES(" + subQueryId + ", '" + c + "', '" + texto + "')";
                    udpRegister.sendSyncUpdate(getVersao(), updateOpcao);
                    c++;
                }
            }

            return codigoPergunta;

        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            System.out.println("[BD] Erro ao criar pergunta: " + e.getMessage());
            return null;
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    private boolean executeInsert(String sql, Object... params) {
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            pstmt.executeUpdate();
            incrementarVersaoDB();

            if (udpRegister != null) {
                String sqlFinal = preencherSql(sql, params);
                udpRegister.sendSyncUpdate(getVersao(), sqlFinal);
            }

            return true;
        } catch (SQLException e) {
            System.out.println("[BD] Erro no registo: " + e.getMessage());
            return false;
        }
    }

    private String preencherSql(String sql, Object... params) {
        String finalSql = sql;
        for (Object p : params) {
            String val = p.toString();
            if (p instanceof String) {
                val = "'" + val.replace("'", "''") + "'";
            }
            finalSql = finalSql.replaceFirst("\\?", val);
        }
        return finalSql;
    }



    public String authenticateUser(String email, String password) {
        String sqlDoc = "SELECT id, nome FROM docente WHERE email = ? AND password = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sqlDoc)) {
            pstmt.setString(1, email);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return "DOCENTE;" + rs.getInt("id") + ";" + rs.getString("nome");
        } catch (SQLException e) { e.printStackTrace(); }

        String sqlEst = "SELECT id, nome, numero FROM estudante WHERE email = ? AND password = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sqlEst)) {
            pstmt.setString(1, email);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return "ESTUDANTE;" + rs.getInt("id") + ";" + rs.getString("nome") + ";" + rs.getInt("numero");
        } catch (SQLException e) { e.printStackTrace(); }

        return null;
    }

    public synchronized boolean updatePergunta(int docenteId, String codigo, String novoEn, String novaCerta, String novaIni, String novaFim, List<String> novasOpcoes) {
        if (hasRespostas(codigo)) return false;

        int perguntaId = -1;
        String checkSql = "SELECT id FROM pergunta WHERE codigo = ? AND docente_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(checkSql)) {
            pstmt.setString(1, codigo);
            pstmt.setInt(2, docenteId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) perguntaId = rs.getInt("id");
            else return false;
        } catch (SQLException e) { return false; }

        try {
            connection.setAutoCommit(false);

            // Update Pergunta
            String updateP = "UPDATE pergunta SET enunciado=?, opcao_certa=?, data_inicio=?, data_fim=? WHERE id=?";
            try (PreparedStatement pstmt = connection.prepareStatement(updateP)) {
                pstmt.setString(1, novoEn); pstmt.setString(2, novaCerta);
                pstmt.setString(3, novaIni); pstmt.setString(4, novaFim);
                pstmt.setInt(5, perguntaId);
                pstmt.executeUpdate();
            }

            // Replace Opções
            try (Statement st = connection.createStatement()) { st.execute("DELETE FROM opcao WHERE pergunta_id=" + perguntaId); }

            String insertO = "INSERT INTO opcao(pergunta_id, codigo, texto) VALUES(?,?,?)";
            char codigoOpcao = 'A';
            try (PreparedStatement pstmt = connection.prepareStatement(insertO)) {
                for (String texto : novasOpcoes) {
                    pstmt.setInt(1, perguntaId);
                    pstmt.setString(2, String.valueOf(codigoOpcao));
                    pstmt.setString(3, texto);
                    pstmt.addBatch();
                    codigoOpcao++;
                }
                pstmt.executeBatch();
            }

            connection.commit();
            incrementarVersaoDB();

            if (udpRegister != null) {
                // 1. Enviar o Update da Pergunta
                String sqlUpdateP = "UPDATE pergunta SET enunciado='" + novoEn + "', opcao_certa='" + novaCerta +
                        "', data_inicio='" + novaIni + "', data_fim='" + novaFim + "' WHERE codigo='" + codigo + "'";
                // Nota: Idealmente usaria ID, mas codigo é mais seguro entre servidores desincronizados
                udpRegister.sendSyncUpdate(getVersao(), sqlUpdateP);

                // 2. Enviar o Delete das Opções
                // Precisamos saber o ID para apagar opções. Vamos usar subquery para ser robusto.
                String subQueryId = "(SELECT id FROM pergunta WHERE codigo='" + codigo + "')";
                String sqlDeleteOp = "DELETE FROM opcao WHERE pergunta_id=" + subQueryId;
                udpRegister.sendSyncUpdate(getVersao(), sqlDeleteOp);

                // 3. Enviar os Inserts das novas Opções
                char c = 'A';
                for (String texto : novasOpcoes) {
                    String sqlInsertOp = "INSERT INTO opcao(pergunta_id, codigo, texto) VALUES(" + subQueryId + ", '" + c + "', '" + texto + "')";
                    udpRegister.sendSyncUpdate(getVersao(), sqlInsertOp);
                    c++;
                }
            }

            return true;
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            return false;
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
        }
    }



    public synchronized boolean deletePergunta(int docenteId, String codigo) {
        if (hasRespostas(codigo)) return false;

        String sql = "DELETE FROM pergunta WHERE codigo = ? AND docente_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, codigo);
            pstmt.setInt(2, docenteId);
            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                incrementarVersaoDB();
                if (udpRegister != null) {
                    String sqlSync = "DELETE FROM pergunta WHERE codigo = '" + codigo + "' AND docente_id = " + docenteId;
                    udpRegister.sendSyncUpdate(getVersao(), sqlSync);
                }
                return true;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }


    private boolean hasRespostas(String codigo) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM resposta r JOIN pergunta p ON r.pergunta_id=p.id WHERE p.codigo=?")) {
            ps.setString(1, codigo);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    // 1. Listar Perguntas do Docente (Vê tudo o que criou)
    public List<String> getPerguntasDocente(int docenteId) {
        List<String> lista = new ArrayList<>();
        String sql = "SELECT id, enunciado, codigo, data_inicio, data_fim FROM pergunta WHERE docente_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, docenteId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                lista.add(String.format("ID: %d | Cod: %s | %s | [%s a %s]",
                        rs.getInt("id"), rs.getString("codigo"), rs.getString("enunciado"),
                        rs.getString("data_inicio"), rs.getString("data_fim")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return lista;
    }

    // 2. Listar Perguntas para Estudante (Apenas ativas e não respondidas)
    public List<String> getPerguntasEstudante(int estudanteId) {
        List<String> lista = new ArrayList<>();
        String sql = "SELECT p.id, p.enunciado, p.data_inicio, p.data_fim FROM pergunta p";

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime agora = LocalDateTime.now();

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int pId = rs.getInt("id");
                String dInicio = rs.getString("data_inicio");
                String dFim = rs.getString("data_fim");

                // Validação de Tempo
                try {
                    LocalDateTime inicio = LocalDateTime.parse(dInicio, formatter);
                    LocalDateTime fim = LocalDateTime.parse(dFim, formatter);

                    if (agora.isAfter(inicio) && agora.isBefore(fim)) {
                        // Verificar se já respondeu
                        if (!jaRespondeu(pId, estudanteId)) {
                            // Buscar opções para exibir
                            String opcoes = getOpcoesString(pId);
                            lista.add(String.format("ID: %d | %s | Opções: %s", pId, rs.getString("enunciado"), opcoes));
                        }
                    }
                } catch (Exception e) {
                    // Ignora erro de parse de data e salta a pergunta
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return lista;
    }

    // Auxiliar: Verifica se aluno já respondeu
    private boolean jaRespondeu(int perguntaId, int estudanteId) {
        String sql = "SELECT id FROM resposta WHERE pergunta_id = ? AND estudante_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, perguntaId);
            pstmt.setInt(2, estudanteId);
            return pstmt.executeQuery().next();
        } catch (SQLException e) { return true; } // Na dúvida assume true para bloquear
    }

    // Auxiliar: Formata opções numa string (ex: "A: Azul, B: Verde")
    private String getOpcoesString(int perguntaId) {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement pstmt = connection.prepareStatement("SELECT codigo, texto FROM opcao WHERE pergunta_id = ? ORDER BY codigo")) {
            pstmt.setInt(1, perguntaId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                sb.append("[").append(rs.getString("codigo")).append("] ").append(rs.getString("texto")).append("  ");
            }
        } catch (SQLException e) {}
        return sb.toString();
    }

    // 3. Submeter Resposta (ANSWER)
    public synchronized String submitAnswer(int estudanteId, int perguntaId, String opcaoEscolhida) {
        // 1. Validar existência da pergunta e datas
        String sqlCheck = "SELECT data_inicio, data_fim FROM pergunta WHERE id = ?";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime agora = LocalDateTime.now();

        try (PreparedStatement pstmt = connection.prepareStatement(sqlCheck)) {
            pstmt.setInt(1, perguntaId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                LocalDateTime inicio = LocalDateTime.parse(rs.getString("data_inicio"), formatter);
                LocalDateTime fim = LocalDateTime.parse(rs.getString("data_fim"), formatter);

                if (agora.isBefore(inicio)) return "ERRO;Pergunta ainda não iniciou.";
                if (agora.isAfter(fim)) return "ERRO;Prazo da pergunta expirou.";
            } else {
                return "ERRO;Pergunta não encontrada.";
            }
        } catch (Exception e) { return "ERRO;Data inválida na BD."; }

        // 2. Validar se já respondeu
        if (jaRespondeu(perguntaId, estudanteId)) {
            return "ERRO;Já respondeu a esta pergunta.";
        }

        // 3. Inserir Resposta
        String dataSubmissao = agora.format(formatter);
        String sqlInsert = "INSERT INTO resposta(pergunta_id, estudante_id, resposta_dada, data_submissao) VALUES(?,?,?,?)";

        // Aqui usamos o método que criámos antes para garantir a Sincronização com o Cluster!
        boolean sucesso = executeInsert(sqlInsert, perguntaId, estudanteId, opcaoEscolhida, dataSubmissao);

        if (sucesso) return "SUCESSO;Resposta registada.";
        return "ERRO;Falha ao gravar na BD.";
    }

    // 4. Gerar CSV (Para Docentes)
    public String getRelatorioCSV(int perguntaId, int docenteId) {
        // Verificar se a pergunta pertence ao docente e se já expirou
        String sqlCheck = "SELECT data_fim, enunciado, opcao_certa, data_inicio FROM pergunta WHERE id = ? AND docente_id = ?";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime agora = LocalDateTime.now();
        StringBuilder csv = new StringBuilder();

        try (PreparedStatement pstmt = connection.prepareStatement(sqlCheck)) {
            pstmt.setInt(1, perguntaId);
            pstmt.setInt(2, docenteId);
            ResultSet rs = pstmt.executeQuery();

            if (!rs.next()) return "ERRO;Pergunta não encontrada ou acesso negado.";

            LocalDateTime fim = LocalDateTime.parse(rs.getString("data_fim"), formatter);
            if (agora.isBefore(fim)) return "ERRO;Pergunta ainda ativa. Aguarde o fim do prazo.";

            // Cabeçalho conforme enunciado (Fig 1)
            // "dia";"hora inicial";"hora final";"enunciado da pergunta";"opção certa"
            String dInicio = rs.getString("data_inicio");
            String[] dataHora = dInicio.split(" "); // Assumindo "YYYY-MM-DD HH:MM"

            csv.append("\"dia\";\"hora inicial\";\"hora final\";\"enunciado\";\"opcao certa\"\n");
            csv.append(String.format("\"%s\";\"%s\";\"%s\";\"%s\";\"%s\"\n\n",
                    dataHora[0], dataHora[1], rs.getString("data_fim").split(" ")[1],
                    rs.getString("enunciado"), rs.getString("opcao_certa")));

            // Lista de Opções
            csv.append("\"opcao\";\"texto da opcao\"\n");
            try (PreparedStatement psOp = connection.prepareStatement("SELECT codigo, texto FROM opcao WHERE pergunta_id = ? ORDER BY codigo")) {
                psOp.setInt(1, perguntaId);
                ResultSet rsOp = psOp.executeQuery();
                while(rsOp.next()) {
                    csv.append(String.format("\"%s\";\"%s\"\n", rsOp.getString("codigo"), rsOp.getString("texto")));
                }
            }
            csv.append("\n");

            // Lista de Respostas
            csv.append("\"numero de estudante\";\"nome\";\"e-mail\";\"resposta\"\n");
            String sqlResp = "SELECT e.numero, e.nome, e.email, r.resposta_dada " +
                    "FROM resposta r JOIN estudante e ON r.estudante_id = e.id " +
                    "WHERE r.pergunta_id = ?";
            try (PreparedStatement psResp = connection.prepareStatement(sqlResp)) {
                psResp.setInt(1, perguntaId);
                ResultSet rsResp = psResp.executeQuery();
                while(rsResp.next()) {
                    csv.append(String.format("\"%d\";\"%s\";\"%s\";\"%s\"\n",
                            rsResp.getInt("numero"), rsResp.getString("nome"),
                            rsResp.getString("email"), rsResp.getString("resposta_dada")));
                }
            }

        } catch (Exception e) { e.printStackTrace(); return "ERRO;Falha ao gerar CSV."; }

        return "SUCESSO_CSV;" + csv.toString().replace("\n", "@@NWL@@");
    }

    private void incrementarVersaoDB() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("UPDATE configuracao SET versao_bd = versao_bd + 1 WHERE id = 1");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public int getVersao() {
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT versao_bd FROM configuracao WHERE id = 1")) {
            if (rs.next()) return rs.getInt("versao_bd");
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public synchronized void executeSyncUpdate(String sql) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("[BD] Sincronização aplicada: " + sql);

            // IMPORTANTE: Atualizar a versão localmente SEM incrementar (para ficar igual ao master)
            // Mas espera, o executeInsert no Master já incrementou.
            // O SQL que vem do master é um INSERT puro. Se executarmos aqui, ele vai inserir.
            // E a versão? O master enviou a versão NOVA.
            // Precisamos de forçar a versão local a ser igual à versão remota.

            // Nota: O método incrementarVersaoDB incrementa +1.
            // Se fizermos o insert aqui, precisamos de garantir que a versão fica batida.
            // O jeito mais fácil é fazer update manual à tabela configuração.

            // Mas espera! O SQL enviado é "INSERT INTO...". Isso NÃO atualiza a tabela configuracao automaticamente neste lado.
            // Temos de atualizar a tabela configuracao manualmente aqui.
            stmt.execute("UPDATE configuracao SET versao_bd = versao_bd + 1 WHERE id = 1");

        } catch (SQLException e) { System.out.println("[BD] Erro sync: " + e.getMessage()); }
    }
}