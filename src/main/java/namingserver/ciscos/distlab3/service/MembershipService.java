package namingserver.ciscos.distlab3.service;

import namingserver.ciscos.distlab3.model.MemberStatus;
import namingserver.ciscos.distlab3.model.NodeState;
import namingserver.ciscos.distlab3.repository.Mappingfunction;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks runtime membership state (heartbeats, SUSPECT/DOWN) alongside the persistent node registry.
 * This is in-memory and resets on server restart (sufficient for lab scope).
 */
@Service
public class MembershipService {

    // Timeouts (ms) — lab friendly defaults
    public static final long HEARTBEAT_INTERVAL_MS = 2000; // 2s
    public static final long SUSPECT_TIMEOUT_MS = 3 * HEARTBEAT_INTERVAL_MS; // 6s
    public static final long DOWN_TIMEOUT_MS = 5 * HEARTBEAT_INTERVAL_MS; // 10s

    private final Mappingfunction repository;
    private final HashService hashService;

    // nodeId -> status
    private final ConcurrentHashMap<Integer, MemberStatus> runtime = new ConcurrentHashMap<>();

    public MembershipService(Mappingfunction repository, HashService hashService) {
        this.repository = repository;
        this.hashService = hashService;
    }

    /**
     * Record a heartbeat from a node. If node not known yet and IP provided, auto-register.
     */
    public MemberStatus recordHeartbeat(String nodeName, String ipOpt) {
        int nodeId = hashService.hash(nodeName);
        Map<Integer, String> nodes = repository.getAllNodes();
        if (!nodes.containsKey(nodeId)) {
            if (ipOpt != null && !ipOpt.isBlank()) {
                // auto-register new node with provided IP
                repository.addNode(nodeId, ipOpt.trim());
                System.out.println("[Membership] Auto-registered node " + nodeName + " (" + nodeId + ") ip=" + ipOpt);
            } else {
                // No registration and no IP — ignore but keep a transient status record
                System.out.println("[Membership] Heartbeat from unknown nodeId=" + nodeId + " (" + nodeName + ") without IP; not registering.");
            }
        }
        String ip = nodes.getOrDefault(nodeId, ipOpt);
        long now = System.currentTimeMillis();
        MemberStatus ms = runtime.compute(nodeId, (k, v) -> {
            if (v == null) return new MemberStatus(k, ip, NodeState.RUNNING, now);
            v.setIp(ip);
            v.setLastHeartbeatMs(now);
            v.setState(NodeState.RUNNING);
            return v;
        });
        return ms;
    }

    /**
     * Returns a snapshot of current membership runtime states, intersected with the persistent registry.
     */
    public List<MemberStatus> listStatuses() {
        List<MemberStatus> out = new ArrayList<>();
        Map<Integer, String> nodes = repository.getAllNodes();
        for (Map.Entry<Integer, String> e : nodes.entrySet()) {
            int id = e.getKey();
            String ip = e.getValue();
            MemberStatus ms = runtime.get(id);
            if (ms == null) {
                ms = new MemberStatus(id, ip, NodeState.SUSPECT, 0L);
            } else if (ms.getIp() == null) {
                ms.setIp(ip);
            }
            out.add(ms);
        }
        return out;
    }

    /**
     * Remove runtime status when node is deleted/left.
     */
    public void removeRuntime(int nodeId) {
        runtime.remove(nodeId);
    }

    @Scheduled(fixedDelay = 1000)
    void monitorTimeouts() {
        long now = System.currentTimeMillis();
        // If a node exists in persistent map but never heartbeated, treat as SUSPECT
        for (Map.Entry<Integer, String> e : repository.getAllNodes().entrySet()) {
            int id = e.getKey();
            MemberStatus ms = runtime.get(id);
            if (ms == null) {
                runtime.putIfAbsent(id, new MemberStatus(id, e.getValue(), NodeState.SUSPECT, 0L));
                continue;
            }
            long age = (ms.getLastHeartbeatMs() == 0L) ? Long.MAX_VALUE : now - ms.getLastHeartbeatMs();
            NodeState prev = ms.getState();
            if (age > DOWN_TIMEOUT_MS) {
                ms.setState(NodeState.DOWN);
            } else if (age > SUSPECT_TIMEOUT_MS) {
                ms.setState(NodeState.SUSPECT);
            } else {
                // Recently alive, keep RUNNING as set in heartbeat path
            }
            if (prev != ms.getState()) {
                System.out.println("[Membership] nodeId=" + id + " state " + prev + " -> " + ms.getState() + " (age=" + age + "ms) @" + Instant.ofEpochMilli(now));
            }
        }
        // Clean up runtime entries for nodes no longer present in persistent map
        runtime.keySet().removeIf(id -> !repository.getAllNodes().containsKey(id));
    }
}
