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
        this.syncPort = tcpPort + 1; // Convenção: Porto sync é TCP + 1
        this.db = db;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress ipDiretoria = InetAddress.getByName(diretoriaIP);
            int portDiretoria = 2300; // Porto fixo da diretoria

            InetAddress ipMulticast = InetAddress.getByName("230.30.30.30");
            int portMulticast = 3030;

            while (true) {
                int versaoDb = db.getVersao();

                // 1. Para a Diretoria (Registo simples)
                String msgDiretoria = "REGISTER_SERVER;" + tcpPort;
                byte[] dataDir = msgDiretoria.getBytes();
                socket.send(new DatagramPacket(dataDir, dataDir.length, ipDiretoria, portDiretoria));

                // 2. Para o Multicast (Sincronização com outros servidores - Heartbeat)
                // Formato: HEARTBEAT;versao_db;porto_clientes;porto_sincronizacao
                String msgMulticast = "HEARTBEAT;" + versaoDb + ";" + tcpPort + ";" + syncPort;
                byte[] dataMulti = msgMulticast.getBytes();
                socket.send(new DatagramPacket(dataMulti, dataMulti.length, ipMulticast, portMulticast));

                // System.out.println("[Servidor] Heartbeat enviado (v" + versaoDb + ")");
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- NOVO: Método para enviar atualizações de escrita ---
    public void sendSyncUpdate(int novaVersao, String sqlQuery) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress ipMulticast = InetAddress.getByName("230.30.30.30");
            int portMulticast = 3030;

            // Formato: UPDATE_DB;versao;sql
            // Nota: SQL pode conter espaços e caracteres especiais, mas o protocolo assume ";" como separador principal no MulticastListener
            String msg = "UPDATE_DB;" + novaVersao + ";" + sqlQuery;
            byte[] data = msg.getBytes();

            DatagramPacket packet = new DatagramPacket(data, data.length, ipMulticast, portMulticast);
            socket.send(packet);

            System.out.println("[Multicast] Update enviado: v" + novaVersao + " -> " + sqlQuery);
        } catch (Exception e) {
            System.out.println("[Multicast] Erro ao enviar update: " + e.getMessage());
        }
    }
}