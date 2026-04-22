package namingserver.ciscos.distlab3.model;

public class MemberStatus {
    private int nodeId;
    private String ip;
    private NodeState state;
    private long lastHeartbeatMs;

    public MemberStatus() {}

    public MemberStatus(int nodeId, String ip, NodeState state, long lastHeartbeatMs) {
        this.nodeId = nodeId;
        this.ip = ip;
        this.state = state;
        this.lastHeartbeatMs = lastHeartbeatMs;
    }

    public int getNodeId() { return nodeId; }
    public void setNodeId(int nodeId) { this.nodeId = nodeId; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public NodeState getState() { return state; }
    public void setState(NodeState state) { this.state = state; }

    public long getLastHeartbeatMs() { return lastHeartbeatMs; }
    public void setLastHeartbeatMs(long lastHeartbeatMs) { this.lastHeartbeatMs = lastHeartbeatMs; }
}
