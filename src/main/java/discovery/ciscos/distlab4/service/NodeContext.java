package discovery.ciscos.distlab4.service;

public class NodeContext {

    private final String nodeName;
    private final String ip;
    private final int currentID;
    private int previousID;
    private int nextID;

    public NodeContext(String nodeName, String ip, int currentID) {
        this.nodeName = nodeName;
        this.ip = ip;
        this.currentID = currentID;
        this.previousID = currentID;
        this.nextID = currentID;
    }

    public String getNodeName() { return nodeName; }
    public String getIp() { return ip; }
    public int getCurrentID() { return currentID; }
    public int getPreviousID() { return previousID; }
    public int getNextID() { return nextID; }
    public void setPreviousID(int previousID) { this.previousID = previousID; }
    public void setNextID(int nextID) { this.nextID = nextID; }
}
