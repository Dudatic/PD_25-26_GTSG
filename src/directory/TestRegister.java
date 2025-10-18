package directory;
import java.net.*;

public class TestRegister {

    static void main() throws Exception {
        DatagramSocket socket = new DatagramSocket();
        InetAddress ip = InetAddress.getByName("127.0.0.1");
        int diretoriaPort = 2300;

        // Teste 1: registar um servidor
        String registerMsg = "REGISTER_SERVER;5000";
        DatagramPacket packet = new DatagramPacket(registerMsg.getBytes(), registerMsg.length(), ip, diretoriaPort);
        socket.send(packet);
        System.out.println("[TESTE] Enviado: " + registerMsg);

        // Recebe resposta da diretoria
        byte[] buffer = new byte[1024];
        DatagramPacket resposta = new DatagramPacket(buffer, buffer.length);
        socket.receive(resposta);

        String respostaStr = new String(resposta.getData(), 0, resposta.getLength());
        System.out.println("[TESTE] Resposta: " + respostaStr);

        // Teste 2: pedir o servidor principal
        String requestMsg = "REQUEST_SERVER";
        DatagramPacket packet2 = new DatagramPacket(requestMsg.getBytes(), requestMsg.length(), ip, diretoriaPort);
        socket.send(packet2);
        socket.receive(resposta);
        respostaStr = new String(resposta.getData(), 0, resposta.getLength());
        System.out.println("[TESTE] Pedido de cliente - " + respostaStr);

        socket.close();
    }
}
