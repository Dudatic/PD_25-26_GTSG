package server;

import java.sql.*;
import java.io.File;

public class Database {
    private final String dbPath;
    private Connection connection;

    public Database(String dbPath) {
        this.dbPath = dbPath;
    }

    public void connect() {
        try {
            // Garante que a diretoria onde vai ficar a BD existe
            File dbFile = new File(dbPath);
            if (dbFile.getParentFile() != null) {
                dbFile.getParentFile().mkdirs();
            }

            // String de conexão JDBC para SQLite
            String url = "jdbc:sqlite:" + dbPath;

            // Estabelecer conexão
            connection = DriverManager.getConnection(url);
            System.out.println("[BD] Conexão a SQLite estabelecida: " + dbPath);

            // IMPORTANTE: O SQLite tem chaves estrangeiras desligadas por defeito.
            // Temos de ativar para garantir a integridade (ex: apagar pergunta apaga opções).
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }

            // Verifica e cria as tabelas necessárias
            createTables();

        } catch (SQLException e) {
            System.out.println("[BD] Erro crítico ao conectar: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // Tabela Docente
            // Requisito: "Criar e editar um registo pessoal (nome, e-mail e password)"
            String sqlDocente = """
                CREATE TABLE IF NOT EXISTS docente (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nome TEXT NOT NULL,
                    email TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL
                );""";
            stmt.execute(sqlDocente);

            // Tabela Estudante
            // Requisito: "não sendo permitida a existência de registos com o mesmo número de estudante ou e-mail"
            String sqlEstudante = """
                CREATE TABLE IF NOT EXISTS estudante (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    numero INTEGER UNIQUE NOT NULL,
                    nome TEXT NOT NULL,
                    email TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL
                );""";
            stmt.execute(sqlEstudante);

            // Tabela Pergunta
            // Requisito: "definindo o enunciado, o número de opções, as opções, a opção correta e o período"
            String sqlPergunta = """
                CREATE TABLE IF NOT EXISTS pergunta (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    docente_id INTEGER,
                    enunciado TEXT NOT NULL,
                    opcao_certa TEXT NOT NULL,
                    data_inicio TEXT,
                    data_fim TEXT,
                    codigo TEXT UNIQUE,
                    FOREIGN KEY(docente_id) REFERENCES docente(id)
                );""";
            stmt.execute(sqlPergunta);

            // Tabela Opção
            // Associada a uma pergunta. Se a pergunta for apagada, as opções também devem ser (ON DELETE CASCADE)
            String sqlOpcao = """
                CREATE TABLE IF NOT EXISTS opcao (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    pergunta_id INTEGER,
                    codigo TEXT NOT NULL, -- Ex: "a", "b", "c"
                    texto TEXT NOT NULL,
                    FOREIGN KEY(pergunta_id) REFERENCES pergunta(id) ON DELETE CASCADE
                );""";
            stmt.execute(sqlOpcao);

            // Tabela Resposta
            // Guarda as respostas dos alunos
            String sqlResposta = """
                CREATE TABLE IF NOT EXISTS resposta (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    pergunta_id INTEGER,
                    estudante_id INTEGER,
                    resposta_dada TEXT NOT NULL,
                    data_submissao TEXT,
                    FOREIGN KEY(pergunta_id) REFERENCES pergunta(id),
                    FOREIGN KEY(estudante_id) REFERENCES estudante(id)
                );""";
            stmt.execute(sqlResposta);

            // Tabela Configuração
            // Requisito: "Versão da BD, Código de registo dos docentes (hash)"
            String sqlConfig = """
                CREATE TABLE IF NOT EXISTS configuracao (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    versao_bd INTEGER DEFAULT 0,
                    codigo_registo_docente TEXT DEFAULT 'admin123' 
                );""";
            stmt.execute(sqlConfig);

            // Inicia a configuração se a tabela estiver vazia (primeira execução)
            stmt.execute("INSERT OR IGNORE INTO configuracao (id, versao_bd, codigo_registo_docente) VALUES (1, 0, 'admin123');");

            System.out.println("[BD] Tabelas verificadas/criadas com sucesso.");
        }
    }

    public Connection getConnection() {
        return connection;
    }
}