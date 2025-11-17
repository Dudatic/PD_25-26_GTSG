package client;

import java.io.IOException;

//Ponto de entrada - controla o fluxo geral do cliente
public class ClientMain {

    public static void main(String[] args) {
        if (args.length != 1) { //Ver se tá a passar o ip da diretoria
            System.out.println("Uso: java ClientMain <ipDiretoria>");
            return;
        }

        String diretoriaIP = args[0];
        DirectoryQuery query = new DirectoryQuery(diretoriaIP); //Cria uma DirectoryQuery para ir buscar o ip e porta
        while (true) {
            String[] servidor = query.getMainServer(); //Vai devolver 2 strings, o IP e a porta

            if (servidor == null) { //Se for null é porque deu errado

                System.out.println("[Cliente] Nenhum servidor disponível. A tentar novamente.");
                try { Thread.sleep(5000);} catch (InterruptedException e) {}
                continue;
            }

            String serverIP = servidor[0]; //IP do servidor
            int serverPort = Integer.parseInt(servidor[1]); //Porta do servidor


            try{
                TCPClient client = new TCPClient(serverIP, serverPort); //Cria um cliente e inicia
                client.start();
            } catch (Exception e) {
                System.out.println("[Cliente] Ligação perdida: " + e.getMessage());

                String[] novoServer = query.getMainServer();
                if(novoServer != null && novoServer[0].equals(serverIP) && Integer.parseInt(novoServer[1]) == serverPort) {
                    System.out.println("[Cliente] Diretoria ainda indica o mesmo servidor, a aguardar 20s...");
                    try{Thread.sleep(20000);} catch (InterruptedException ex) {}



                }

            }


        }

    }
}
