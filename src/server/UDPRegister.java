package server;

import java.net.*;


public class UDPRegister implements Runnable {
    private final String diretoriaIP;
    private final int tcpPort;

    public UDPRegister(String diretoriaIP, int tcpPort) {
        this.diretoriaIP = diretoriaIP;
        this.tcpPort = tcpPort;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress ip = InetAddress.getByName(diretoriaIP);
            int diretoriaPort = 2300; // Porta fixa da diretoria

            while (true) {
                String msg = "REGISTER_SERVER;" + tcpPort;
                DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), ip, diretoriaPort);
                socket.send(packet);
                System.out.println("[Servidor] Enviado heartbeat para diretoria...");

                Thread.sleep(10000); // envia a cada 10 segundos
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
