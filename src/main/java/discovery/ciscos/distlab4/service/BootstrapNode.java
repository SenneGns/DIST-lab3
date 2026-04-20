package discovery.ciscos.distlab4.service;

import discovery.ciscos.distlab4.service.DiscoveryService;

public class BootstrapNode {
    public String Nodename;
    public String IP;
    public String Groupaddress = "224.0.0.100";
    public final int port = 4567;
    private DiscoveryService discovery;

    // constructor
    public BootstrapNode(String Nodename, String IP) {
        this.Nodename = Nodename;
        this.IP = IP;
    }

    // methods
    public String getNodeName() {
        return Nodename;
    }
    public String getIP() {
        return IP;
    }

    public void bootstrap (){
        String message = this.Nodename + ":" + this.IP;
        this.discovery.sendMulticast(message,Groupaddress,port);
    }
}
