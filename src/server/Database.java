package server;

import java.sql.*;
import java.io.File;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class Database {
    private final String dbPath;
    private final ServerTCP server; // Referência para enviar multicast
    private Connection connection;

    public Database(String dbPath, ServerTCP server) {
        this.dbPath = dbPath;
        this.server = server;
    }

    // Construtor auxiliar
    public Database(String dbPath) {
        this.dbPath = dbPath;
        this.server = null;
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

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS docente (id INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT NOT NULL, email TEXT UNIQUE NOT NULL, password TEXT NOT NULL);");
            stmt.execute("CREATE TABLE IF NOT EXISTS estudante (id INTEGER PRIMARY KEY AUTOINCREMENT, numero INTEGER UNIQUE NOT NULL, nome TEXT NOT NULL, email TEXT UNIQUE NOT NULL, password TEXT NOT NULL);");
            stmt.execute("CREATE TABLE IF NOT EXISTS pergunta (id INTEGER PRIMARY KEY AUTOINCREMENT, docente_id INTEGER, enunciado TEXT NOT NULL, opcao_certa TEXT NOT NULL, data_inicio TEXT, data_fim TEXT, codigo TEXT UNIQUE, FOREIGN KEY(docente_id) REFERENCES docente(id) ON DELETE CASCADE);");
            stmt.execute("CREATE TABLE IF NOT EXISTS opcao (id INTEGER PRIMARY KEY AUTOINCREMENT, pergunta_id INTEGER, codigo TEXT NOT NULL, texto TEXT NOT NULL, FOREIGN KEY(pergunta_id) REFERENCES pergunta(id) ON DELETE CASCADE);");
            stmt.execute("CREATE TABLE IF NOT EXISTS resposta (id INTEGER PRIMARY KEY AUTOINCREMENT, pergunta_id INTEGER, estudante_id INTEGER, resposta_dada TEXT NOT NULL, data_submissao TEXT, FOREIGN KEY(pergunta_id) REFERENCES pergunta(id) ON DELETE CASCADE, FOREIGN KEY(estudante_id) REFERENCES estudante(id));");

            stmt.execute("CREATE TABLE IF NOT EXISTS configuracao (id INTEGER PRIMARY KEY CHECK (id = 1), versao_bd INTEGER DEFAULT 0, codigo_registo_docente TEXT DEFAULT 'admin123');");
            stmt.execute("INSERT OR IGNORE INTO configuracao (id, versao_bd, codigo_registo_docente) VALUES (1, 0, 'admin123');");
        }
    }

    // --- GESTÃO DE UTILIZADORES ---

    public boolean checkDocenteCode(String code) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT codigo_registo_docente FROM configuracao WHERE id = 1")) {
            if (rs.next()) return rs.getString("codigo_registo_docente").equals(code);
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public synchronized boolean registerDocente(String nome, String email, String password) {
        String sql = "INSERT INTO docente(nome, email, password) VALUES(?,?,?)";
        if (executeUpdateOrInsert(sql, nome, email, password)) {
            String sqlReplica = String.format("INSERT INTO docente(nome, email, password) VALUES('%s', '%s', '%s')", nome, email, password);
            propagateSQL(sqlReplica);
            return true;
        }
        return false;
    }

    public synchronized boolean registerEstudante(int numero, String nome, String email, String password) {
        String sql = "INSERT INTO estudante(numero, nome, email, password) VALUES(?,?,?,?)";
        if (executeUpdateOrInsert(sql, numero, nome, email, password)) {
            String sqlReplica = String.format("INSERT INTO estudante(numero, nome, email, password) VALUES(%d, '%s', '%s', '%s')", numero, nome, email, password);
            propagateSQL(sqlReplica);
            return true;
        }
        return false;
    }

    public synchronized boolean updateDocente(int id, String nome, String email, String password) {
        String sql = "UPDATE docente SET nome = ?, email = ?, password = ? WHERE id = ?";
        if (executeUpdateOrInsert(sql, nome, email, password, id)) {
            String sqlReplica = String.format("UPDATE docente SET nome='%s', email='%s', password='%s' WHERE id=%d", nome, email, password, id);
            propagateSQL(sqlReplica);
            return true;
        }
        return false;
    }

    public synchronized boolean updateEstudante(int id, String nome, String email, String password) {
        String sql = "UPDATE estudante SET nome = ?, email = ?, password = ? WHERE id = ?";
        if (executeUpdateOrInsert(sql, nome, email, password, id)) {
            String sqlReplica = String.format("UPDATE estudante SET nome='%s', email='%s', password='%s' WHERE id=%d", nome, email, password, id);
            propagateSQL(sqlReplica);
            return true;
        }
        return false;
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

    // --- PERGUNTAS (DOCENTE) ---

    public synchronized String createPergunta(int docenteId, String enunciado, String opcaoCerta, String dataInicio, String dataFim, List<String> opcoes) {
        String codigoPergunta = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String sqlPergunta = "INSERT INTO pergunta(docente_id, enunciado, opcao_certa, data_inicio, data_fim, codigo) VALUES(?,?,?,?,?,?)";
        String sqlOpcao = "INSERT INTO opcao(pergunta_id, codigo, texto) VALUES(?,?,?)";

        try {
            connection.setAutoCommit(false);
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
                if (rs.next()) perguntaId = rs.getInt(1);
            }

            if (perguntaId == -1) { connection.rollback(); return null; }

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

            // --- REPLICAÇÃO ---
            String sqlRepPergunta = String.format("INSERT INTO pergunta(docente_id, enunciado, opcao_certa, data_inicio, data_fim, codigo) VALUES(%d, '%s', '%s', '%s', '%s', '%s')",
                    docenteId, enunciado, opcaoCerta, dataInicio, dataFim, codigoPergunta);
            propagateSQL(sqlRepPergunta);

            char codOp = 'A';
            for (String txt : opcoes) {
                String sqlRepOp = String.format("INSERT INTO opcao(pergunta_id, codigo, texto) VALUES((SELECT id FROM pergunta WHERE codigo='%s'), '%s', '%s')",
                        codigoPergunta, String.valueOf(codOp++), txt);
                propagateSQL(sqlRepOp);
            }
            // ------------------

            return codigoPergunta;
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            return null;
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
        }
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

            String updateP = "UPDATE pergunta SET enunciado=?, opcao_certa=?, data_inicio=?, data_fim=? WHERE id=?";
            try (PreparedStatement pstmt = connection.prepareStatement(updateP)) {
                pstmt.setString(1, novoEn); pstmt.setString(2, novaCerta);
                pstmt.setString(3, novaIni); pstmt.setString(4, novaFim);
                pstmt.setInt(5, perguntaId);
                pstmt.executeUpdate();
            }

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

            // --- REPLICAÇÃO ---
            String sqlRepUpd = String.format("UPDATE pergunta SET enunciado='%s', opcao_certa='%s', data_inicio='%s', data_fim='%s' WHERE codigo='%s'",
                    novoEn, novaCerta, novaIni, novaFim, codigo);
            propagateSQL(sqlRepUpd);

            propagateSQL("DELETE FROM opcao WHERE pergunta_id=(SELECT id FROM pergunta WHERE codigo='" + codigo + "')");
            char cOp = 'A';
            for (String txt : novasOpcoes) {
                String sqlRepOp = String.format("INSERT INTO opcao(pergunta_id, codigo, texto) VALUES((SELECT id FROM pergunta WHERE codigo='%s'), '%s', '%s')",
                        codigo, String.valueOf(cOp++), txt);
                propagateSQL(sqlRepOp);
            }
            // ------------------

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
                propagateSQL(String.format("DELETE FROM pergunta WHERE codigo='%s'", codigo));
                return true;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public String getPerguntasDocente(int docenteId) {
        StringBuilder sb = new StringBuilder();
        String sql = "SELECT codigo, enunciado, data_inicio, data_fim FROM pergunta WHERE docente_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, docenteId);
            ResultSet rs = pstmt.executeQuery();
            boolean has = false;
            while (rs.next()) {
                has = true;
                sb.append("[").append(rs.getString("codigo")).append("] ")
                        .append(rs.getString("enunciado")).append(" | ")
                        .append(rs.getString("data_inicio")).append(" -> ")
                        .append(rs.getString("data_fim")).append("#");
            }
            if (!has) return "Sem perguntas criadas.";
        } catch (SQLException e) { return "Erro: " + e.getMessage(); }
        return sb.toString();
    }

    public String getRespostasCSV(int docenteId, String codigoPergunta) {
        String checkSql = "SELECT id FROM pergunta WHERE codigo = ? AND docente_id = ?";
        int pId = -1;
        try (PreparedStatement ps = connection.prepareStatement(checkSql)) {
            ps.setString(1, codigoPergunta); ps.setInt(2, docenteId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) pId = rs.getInt("id"); else return null;
        } catch (SQLException e) { return null; }

        StringBuilder csv = new StringBuilder("Estudante,Numero,Data,Resposta,Cotacao\n");
        String sql = "SELECT e.nome, e.numero, r.data_submissao, r.resposta_dada, p.opcao_certa FROM resposta r JOIN estudante e ON r.estudante_id=e.id JOIN pergunta p ON r.pergunta_id=p.id WHERE r.pergunta_id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, pId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int cotacao = rs.getString("resposta_dada").equals(rs.getString("opcao_certa")) ? 1 : 0;
                csv.append(rs.getString("nome")).append(",").append(rs.getInt("numero")).append(",")
                        .append(rs.getString("data_submissao")).append(",").append(rs.getString("resposta_dada"))
                        .append(",").append(cotacao).append("\n");
            }
        } catch (SQLException e) { return null; }
        return csv.toString();
    }

    public String getHistoricoEstudante(int estudanteId) {
        StringBuilder sb = new StringBuilder();
        String sql = "SELECT p.codigo, p.enunciado, r.resposta_dada, p.opcao_certa, r.data_submissao FROM resposta r JOIN pergunta p ON r.pergunta_id = p.id WHERE r.estudante_id = ? ORDER BY r.data_submissao DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, estudanteId);
            ResultSet rs = ps.executeQuery();
            boolean has = false;
            while (rs.next()) {
                has = true;
                String status = rs.getString("resposta_dada").equals(rs.getString("opcao_certa")) ? "CERTA" : "ERRADA (Era " + rs.getString("opcao_certa") + ")";
                sb.append("[").append(rs.getString("data_submissao")).append("] ")
                        .append(rs.getString("enunciado")).append(" (").append(rs.getString("codigo")).append(")")
                        .append(" -> Resposta: ").append(rs.getString("resposta_dada"))
                        .append(" | ").append(status).append("#");
            }
            if (!has) return "Sem historico.";
        } catch (SQLException e) { return "Erro: " + e.getMessage(); }
        return sb.toString();
    }

    public boolean isPerguntaAtiva(String codigo) {
        String sql = "SELECT data_inicio, data_fim FROM pergunta WHERE codigo = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, codigo);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                try {
                    LocalDateTime ini = LocalDateTime.parse(rs.getString("data_inicio"), f);
                    LocalDateTime fim = LocalDateTime.parse(rs.getString("data_fim"), f);
                    LocalDateTime now = LocalDateTime.now();
                    return now.isAfter(ini) && now.isBefore(fim);
                } catch (DateTimeParseException e) { return false; }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public String getPerguntaTexto(String codigo) {
        StringBuilder sb = new StringBuilder();
        int pId = -1;
        try (PreparedStatement ps = connection.prepareStatement("SELECT id, enunciado, data_inicio, data_fim FROM pergunta WHERE codigo=?")) {
            ps.setString(1, codigo);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                pId = rs.getInt("id");
                sb.append("PERGUNTA: ").append(rs.getString("enunciado")).append(";Valida: ").append(rs.getString("data_inicio")).append(" ate ").append(rs.getString("data_fim")).append(";");
            } else return null;
        } catch (SQLException e) { return null; }

        try (PreparedStatement ps = connection.prepareStatement("SELECT codigo, texto FROM opcao WHERE pergunta_id=? ORDER BY codigo")) {
            ps.setInt(1, pId);
            ResultSet rs = ps.executeQuery();
            sb.append("OPCOES:");
            while (rs.next()) {
                sb.append("[").append(rs.getString("codigo")).append("] ").append(rs.getString("texto")).append(" | ");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return sb.toString();
    }

    public synchronized boolean submitAnswer(int estudanteId, String codigo, String resposta) {
        int pId = -1;
        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM pergunta WHERE codigo=?")) {
            ps.setString(1, codigo);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) pId = rs.getInt("id"); else return false;
        } catch (SQLException e) { return false; }

        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM resposta WHERE pergunta_id=? AND estudante_id=?")) {
            ps.setInt(1, pId); ps.setInt(2, estudanteId);
            if (ps.executeQuery().next()) return false;
        } catch (SQLException e) { return false; }

        String sql = "INSERT INTO resposta(pergunta_id, estudante_id, resposta_dada, data_submissao) VALUES(?,?,?, datetime('now'))";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, pId); ps.setInt(2, estudanteId); ps.setString(3, resposta);
            ps.executeUpdate();
            incrementarVersaoDB();

            String sqlRep = String.format("INSERT INTO resposta(pergunta_id, estudante_id, resposta_dada, data_submissao) VALUES((SELECT id FROM pergunta WHERE codigo='%s'), %d, '%s', datetime('now'))",
                    codigo, estudanteId, resposta);
            propagateSQL(sqlRep);

            return true;
        } catch (SQLException e) { return false; }
    }

    // --- SINCRONIZAÇÃO E UTILITÁRIOS ---

    private void propagateSQL(String sql) {
        if (server != null) {
            // FIX: Envia agora o porto TCP para que o listener saiba ignorar a própria mensagem
            server.sendMulticast("UPDATE_DB;" + server.getTcpPort() + ";" + sql);
        }
    }

    private boolean executeUpdateOrInsert(String sql, Object... params) {
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) pstmt.setObject(i + 1, params[i]);
            if (pstmt.executeUpdate() > 0) {
                incrementarVersaoDB();
                return true;
            }
        } catch (SQLException e) { System.out.println("[BD] SQL Error: " + e.getMessage()); }
        return false;
    }

    private void incrementarVersaoDB() {
        try (Statement s = connection.createStatement()) {
            s.execute("UPDATE configuracao SET versao_bd = versao_bd + 1 WHERE id = 1");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public int getVersao() {
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT versao_bd FROM configuracao WHERE id = 1")) {
            if (rs.next()) return rs.getInt("versao_bd");
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public synchronized void executeSyncUpdate(String sql) {
        try (Statement s = connection.createStatement()) {
            s.execute(sql);
            incrementarVersaoDB();
            System.out.println("[BD] Sync aplicado: " + sql);
        } catch (SQLException e) { System.out.println("[BD] Sync error: " + e.getMessage() + " SQL: " + sql); }
    }

    private boolean hasRespostas(String codigo) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM resposta r JOIN pergunta p ON r.pergunta_id=p.id WHERE p.codigo=?")) {
            ps.setString(1, codigo);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[BD] Conexão fechada.");
            }
        } catch (SQLException e) {
            System.out.println("[BD] Erro ao fechar conexão: " + e.getMessage());
        }
    }

    public String getDbPath() {
        return dbPath;
    }
}