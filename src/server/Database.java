package server;

import java.sql.*;
import java.io.File;
import java.util.List;
import java.util.UUID;

public class Database {
    private final String dbPath;
    private Connection connection;

    public Database(String dbPath) {
        this.dbPath = dbPath;
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
        // Gera um código único curto (ex: primeiros 6 chars de um UUID)
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
            // As opções vêm numa lista simples. Vamos dar códigos automáticos (A, B, C...)
            char codigoOpcao = 'A';
            try (PreparedStatement pstmtOp = connection.prepareStatement(sqlOpcao)) {
                for (String textoOpcao : opcoes) {
                    pstmtOp.setInt(1, perguntaId);
                    pstmtOp.setString(2, String.valueOf(codigoOpcao)); // "A", "B", etc.
                    pstmtOp.setString(3, textoOpcao);
                    pstmtOp.addBatch(); // Adiciona ao lote
                    codigoOpcao++;
                }
                pstmtOp.executeBatch(); // Executa todas de uma vez
            }

            connection.commit(); // Confirma tudo
            incrementarVersaoDB(); // Atualiza versão para sincronização
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
            return true;
        } catch (SQLException e) {
            System.out.println("[BD] Erro no registo: " + e.getMessage());
            return false;
        }
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

    public void executeSyncUpdate(String sql) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("[BD] Sincronização aplicada.");
        } catch (SQLException e) { System.out.println("[BD] Erro sync: " + e.getMessage()); }
    }
}