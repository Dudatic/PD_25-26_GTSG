package server;

public class ServerMain {

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Uso: java ServerMain <portoTCP> <ipDiretoria> <caminhoBD>");
            return;
        }

        int tcpPort = Integer.parseInt(args[0]);
        String diretoriaIP = args[1];
        String dbPath = args[2];

        // A inicialização da BD passou para dentro do ServerTCP para centralizar o acesso
        ServerTCP server = new ServerTCP(tcpPort, diretoriaIP, dbPath);
        server.start();
    }
}