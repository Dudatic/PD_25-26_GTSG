package directory;

import java.util.List;

//Corre em loop infinito como thread separada

public class CleanupTask implements Runnable {
    private final List<ServerInfo> servers;

    public CleanupTask(List<ServerInfo> servers) {
        this.servers = servers;
    }

    @Override
    public void run() { //A cada iteração bloqueia (synchronized(servers)) para evitar race conditions
        while (true) {
            System.out.println("[Diretoria] Servidores ativos: " + servers.size());
            synchronized (servers) {
                long agora = System.currentTimeMillis();
                servers.removeIf(s -> agora - s.getLastHeartbeat() > 17000);    //Remove servidor cujo lastHeartbeat foi há mais de 17000 ms (17s)
            }

            try {
                Thread.sleep(5000); //diminui a latência de remoção e reduz overhead
            } catch (InterruptedException e) {
                return;
            }


        }
    }
}
