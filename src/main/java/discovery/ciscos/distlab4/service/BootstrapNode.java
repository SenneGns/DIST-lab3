package discovery.ciscos.distlab4.service;

public class BootstrapNode {
    private final String nodeName;
    private final String ip;
    private final DiscoveryService discovery = new DiscoveryService();

    // constructor
    public BootstrapNode(String nodeName, String ip) {
        this.nodeName = nodeName;
        this.ip = ip;
    }

    // getters
    public String getNodeName() { return nodeName; }
    public String getIP() { return ip; }

    public void bootstrap() {
        // Send BOOTSTRAP:<name>:<ip> to the agreed multicast group
        discovery.sendBootstrap(nodeName, ip);
    }
}
