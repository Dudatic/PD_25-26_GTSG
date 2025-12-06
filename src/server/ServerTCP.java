package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerTCP extends Thread {
    private final int port;
    private final String diretoriaIP;
    private final String dbPath;
    private Database db;
    private UDPRegister udpRegister; // Guardar referência
    private boolean running = true;

    public ServerTCP(int port, String diretoriaIP, String dbPath) {
        this.port = port;
        this.diretoriaIP = diretoriaIP;
        this.dbPath = dbPath;
    }

    @Override
    public void run() {
        // 1. Iniciar Base de Dados
        db = new Database(dbPath);
        db.connect();

        // 2. Iniciar sistema de registo e multicast
        // Passamos a DB para ele ler a versão, mas ainda não ligamos a escrita
        udpRegister = new UDPRegister(diretoriaIP, port, db);
        new Thread(udpRegister).start();

        // 3. IMPORTANTE: Ligar o UDPRegister à BD para permitir notificações de escrita
        db.setUdpRegister(udpRegister);

        // 4. Iniciar Listener Multicast (para receber updates de outros)
        new Thread(new MulticastListener(db, port, dbPath)).start();

        // 5. Iniciar Servidor de Ficheiros (para fornecer cópia da DB)
        new Thread(new FileSyncServer(port + 1, dbPath)).start();

        System.out.println("[ServidorTCP] À escuta no porto " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (running) {
                Socket clientSocket = serverSocket.accept();
                // Cria uma nova thread para cada cliente
                new Thread(new ClientHandler(clientSocket, db)).start();
            }
        } catch (IOException e) {
            System.out.println("[ServidorTCP] Erro: " + e.getMessage());
        }
    }
}