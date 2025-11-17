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
            InetAddress ipDiretoria = InetAddress.getByName(diretoriaIP);
            int portDiretoria = 2300; // Porta fixa da diretoria

            InetAddress ipMulticast = InetAddress.getByName("230.30.30.30");
            int portMulticast = 3030;

            while (true) {
                String msg = "REGISTER_SERVER;" + tcpPort;
                byte[] data = msg.getBytes();

                DatagramPacket packetDiretoria = new DatagramPacket(data, data.length, ipDiretoria, portDiretoria);
                socket.send(packetDiretoria);

                DatagramPacket packetMulticast = new DatagramPacket(data, data.length, ipMulticast, portMulticast);
                socket.send(packetMulticast);

                System.out.println("[Servidor] Enviado heartbeat para (Diretoria + Multicast...)");

                Thread.sleep(5000); // envia a cada 10 segundos
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
