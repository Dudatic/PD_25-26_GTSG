package server;

import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ServerTCP {
    private final int tcpPort;
    private final String diretoriaIP;
    private final Database db;
    private final String dbPath;

    private final List<PrintWriter> activeClients = new ArrayList<>();

    public ServerTCP(int tcpPort, String diretoriaIP, String dbPath) {
        this.tcpPort = tcpPort;
        this.diretoriaIP = diretoriaIP;
        this.dbPath = dbPath;
        // Passamos 'this' para que a Database possa chamar sendMulticast e pegar o porto
        this.db = new Database(dbPath, this);
        this.db.connect();
    }

    public void start() {
        System.out.println("[Servidor] A iniciar componentes...");

        new Thread(new UDPRegister(diretoriaIP, tcpPort, db)).start();
        new Thread(new MulticastListener(db, tcpPort, dbPath)).start();
        new Thread(new FileSyncServer(tcpPort + 1, dbPath)).start();

        System.out.println("[Servidor] À escuta de clientes no porto TCP " + tcpPort);
        try (ServerSocket serverSocket = new ServerSocket(tcpPort)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket, db, this)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void addClient(PrintWriter out) {
        activeClients.add(out);
    }

    public synchronized void removeClient(PrintWriter out) {
        activeClients.remove(out);
    }

    public synchronized void broadcast(String mensagem) {
        for (PrintWriter client : activeClients) {
            client.println("NOTIFICACAO;" + mensagem);
        }
    }

    // Novo método para enviar SQL/Heartbeats para o cluster
    public void sendMulticast(String message) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress group = InetAddress.getByName("230.30.30.30");
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, 3030);
            socket.send(packet);
        } catch (IOException e) {
            System.out.println("[ServerTCP] Erro multicast: " + e.getMessage());
        }
    }

    public int getTcpPort() {
        return tcpPort;
    }
}