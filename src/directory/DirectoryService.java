package directory;

import java.net.*;
import java.util.*;

public class DirectoryService {

    private final int portUDP;  //Porto onde escuta
    private final List<ServerInfo> servidores = Collections.synchronizedList(new ArrayList<>());
    //Lista de servudores, garante operações atómicas em nível de lista, ainda assim usamos synchronized para iterações e manipulações compostas

    public DirectoryService(int portUDP) {
        this.portUDP = portUDP;
    }

    public void start() {
        // Thread que remove servidores inativos
        new Thread(new CleanupTask(servidores)).start(); //Cria um CleanupTask numa thread em separado

        try (DatagramSocket socket = new DatagramSocket(portUDP)) { //Cria um DatagramSocket e entra num loop infinito para ler mensagens
            System.out.println("[Diretoria] Serviço iniciado no porto UDP " + portUDP);

            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); //Vai esperar aqui que um user se junte

                String mensagem = new String(packet.getData(), 0, packet.getLength());
                processarMensagem(mensagem.trim(), packet, socket);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processarMensagem(String msg, DatagramPacket packet, DatagramSocket socket) throws Exception { //Processa a mensagem do user
        InetAddress ip = packet.getAddress();
        int port = packet.getPort();

        // Servidor a registar-se: REGISTER_SERVER;<portoTCP>
        if (msg.startsWith("REGISTER_SERVER")) {
            String[] partes = msg.split(";");
            int tcpPort = Integer.parseInt(partes[1]);

            synchronized (servidores) {
                Optional<ServerInfo> existente = servidores.stream()
                        .filter(s -> s.getIp().equals(ip.getHostAddress()) && s.getTcpPort() == tcpPort)
                        .findFirst();

                if (existente.isPresent()) {//Se já existir dá HeartBeat
                    existente.get().updateHeartbeat();
                } else {//Se não adiciona à lista
                    servidores.add(new ServerInfo(ip.getHostAddress(), tcpPort));
                    System.out.println("[Diretoria] Servidor registado: " + ip.getHostAddress() + ":" + tcpPort);
                }
            }

            enviarServidorPrincipal(socket, ip, port);
        }

        // Cliente a pedir servidor principal
        else if (msg.equals("REQUEST_SERVER")) {
            enviarServidorPrincipal(socket, ip, port);
        }
    }

    private void enviarServidorPrincipal(DatagramSocket socket, InetAddress ip, int port) throws Exception { //envia servidor principal
        if (servidores.isEmpty()) {
            String resposta = "NO_SERVERS";
            socket.send(new DatagramPacket(resposta.getBytes(), resposta.length(), ip, port));
            return;
        }

        ServerInfo principal = servidores.get(0);
        String resposta = "MAIN_SERVER;" + principal.getIp() + ";" + principal.getTcpPort();
        socket.send(new DatagramPacket(resposta.getBytes(), resposta.length(), ip, port));
    }
}

