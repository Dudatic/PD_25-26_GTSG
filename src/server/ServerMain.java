package server;

public class ServerMain {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Uso: java ServerMain <portoTCP> <ipDiretoria>");
            return;
        }

        int tcpPort = Integer.parseInt(args[0]);
        String diretoriaIP = args[1];

        ServerTCP server = new ServerTCP(tcpPort, diretoriaIP);
        server.start();
    }

}
