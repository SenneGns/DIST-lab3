package namingserver.ciscos.distlab3.model;

public class NodeInfo {

    private int id;          // hash van de node naam (0 - 32768)
    private String name;     // naam van de node (bv. "node1")
    private String ipAddress; // IP adres van de node (bv. "192.168.1.1")

    // Lege constructor — nodig voor JSON serialisatie
    public NodeInfo() {}

    public NodeInfo(int id, String name, String ipAddress) {
        this.id = id;
        this.name = name;
        this.ipAddress = ipAddress;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    @Override
    public String toString() {
        return "NodeInfo{id=" + id + ", name='" + name + "', ip='" + ipAddress + "'}";
    }
}
