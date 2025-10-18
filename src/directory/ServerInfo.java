package directory;


//Guarda ip, tcpPort (porto TCP onde aceita clientes)

public class ServerInfo {
    private final String ip;
    private final int tcpPort;
    private long lastHeartbeat; //tempo em ms desde a ultima atualização

    public ServerInfo(String ip, int tcpPort) {
        this.ip = ip;
        this.tcpPort = tcpPort;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public String getIp() { return ip; }
    public int getTcpPort() { return tcpPort; }
    public long getLastHeartbeat() { return lastHeartbeat; }

    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return ip + ":" + tcpPort;
    }
}
