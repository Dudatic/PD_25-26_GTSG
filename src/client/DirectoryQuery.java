package client;

import java.net.*;

//Comunica com a diretoria (UDP) para descobrir o IP/porto do servidor
public class DirectoryQuery {
    private final String diretoriaIP;

    public DirectoryQuery(String diretoriaIP) {
        this.diretoriaIP = diretoriaIP;
    }

    public String[] getMainServer() {


        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress ip = InetAddress.getByName(diretoriaIP);
            int diretoriaPort = 2300;
            //Cria um socket UDP local e define para onde vai enviar (IP e Porta)

            //Vai pedir o server à diretoria
            String msg = "REQUEST_SERVER";
            DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), ip, diretoriaPort);
            socket.send(packet);

            //Recebe a resposta da diretoria
            byte[] buffer = new byte[1024];
            DatagramPacket resposta = new DatagramPacket(buffer, buffer.length);
            socket.receive(resposta);

            //Se a resposta for correta ele vai devolver o valor para o ClientMain
            String respostaStr = new String(resposta.getData(), 0, resposta.getLength());
            if (respostaStr.startsWith("MAIN_SERVER")) {
                String[] partes = respostaStr.split(";");
                return new String[]{partes[1], partes[2]};
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
