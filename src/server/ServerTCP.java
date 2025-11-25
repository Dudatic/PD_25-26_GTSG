package server;
import java.net.*;
import java.io.*;

public class ServerTCP {
    private final int tcpPort;
    private final String diretoriaIP;
    private final Database db;
    private final String dbPath; // Guardar o caminho para passar às threads

    // Construtor atualizado para receber o dbPath
    public ServerTCP(int tcpPort, String diretoriaIP, String dbPath) {
        this.tcpPort = tcpPort;
        this.diretoriaIP = diretoriaIP;
        this.dbPath = dbPath;

        this.db = new Database(dbPath);
        this.db.connect();
    }

    public void start() {
        System.out.println("[Servidor] A iniciar componentes...");

        // 1. Envia Heartbeats (UDP)
        new Thread(new UDPRegister(diretoriaIP, tcpPort, db)).start();

        // 2. Escuta Multicast (Sincronização - recebe atualizações)
        new Thread(new MulticastListener(db, tcpPort, dbPath)).start();

        // 3. Servidor de Ficheiros (Permite que outros saquem a BD daqui)
        // Porto de sync = tcpPort + 1
        new Thread(new FileSyncServer(tcpPort + 1, dbPath)).start();

        // 4. Aceita Clientes (TCP) - Bloqueante
        System.out.println("[Servidor] À escuta de clientes no porto TCP " + tcpPort);
        try (ServerSocket serverSocket = new ServerSocket(tcpPort)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket, db)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}