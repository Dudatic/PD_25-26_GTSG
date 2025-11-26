package server;

import java.net.*;

public class UDPRegister implements Runnable {
    private final String diretoriaIP;
    private final int tcpPort;
    private final int syncPort;
    private final Database db;

    public UDPRegister(String diretoriaIP, int tcpPort, Database db) {
        this.diretoriaIP = diretoriaIP;
        this.tcpPort = tcpPort;
        this.syncPort = tcpPort + 1;
        this.db = db;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress ipDiretoria = InetAddress.getByName(diretoriaIP);
            int portDiretoria = 2300;
            InetAddress ipMulticast = InetAddress.getByName("230.30.30.30");
            int portMulticast = 3030;

            while (true) {
                int versaoDb = db.getVersao();
                String msgDiretoria = "REGISTER_SERVER;" + tcpPort;
                byte[] dataDir = msgDiretoria.getBytes();
                socket.send(new DatagramPacket(dataDir, dataDir.length, ipDiretoria, portDiretoria));

                String msgMulticast = "HEARTBEAT;" + versaoDb + ";" + tcpPort + ";" + syncPort;
                byte[] dataMulti = msgMulticast.getBytes();
                socket.send(new DatagramPacket(dataMulti, dataMulti.length, ipMulticast, portMulticast));

                // System.out.println("[Servidor] Heartbeat enviado (v" + versaoDb + ")");
                Thread.sleep(5000);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}