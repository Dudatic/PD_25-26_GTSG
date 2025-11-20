package server;

public class ServerMain {

    public static void main(String[] args) {
        if (args.length != 3) { //porto, idDiretoria, caminhoBD
            System.out.println("Uso: java ServerMain <portoTCP> <ipDiretoria>");
            return;
        }

        int tcpPort = Integer.parseInt(args[0]);
        String diretoriaIP = args[1];
        String dbPath = args[2];

        Database db = new Database(dbPath);
        db.connect();

        ServerTCP server = new ServerTCP(tcpPort, diretoriaIP, db);
        server.start();
    }

}
