package namingserver.ciscos.distlab3.controller;

import namingserver.ciscos.distlab3.model.FileLookupResponse;
import namingserver.ciscos.distlab3.model.MemberStatus;
import namingserver.ciscos.distlab3.service.MembershipService;
import namingserver.ciscos.distlab3.service.NamingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/naming")
public class Controller {

    private final NamingService namingService;
    private final MembershipService membershipService;

    // CONSTRUCTOR
    public Controller(NamingService namingService, MembershipService membershipService) {
        this.namingService = namingService;
        this.membershipService = membershipService;
    }

    /**
     * Lookup which node owns a file.
     * GET /naming/lookup?filename=example.txt
     */
    @GetMapping("/lookup")
    public ResponseEntity<?> lookup(@RequestParam String filename) {
        try {
            FileLookupResponse response = namingService.findOwner(filename);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(e.getMessage());
        }
    }

    /**
     * Register a new node.
     * POST /naming/nodes?nodeName=node1&ip=192.168.1.1
     */
    @PostMapping("/nodes")
    public ResponseEntity<String> registerNode(@RequestParam String nodeName, @RequestParam String ip) {
        try {
            namingService.registerNode(nodeName, ip);
            return ResponseEntity.status(201).body("Node registered successfully");
        }catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(e.getMessage());
        }
    }

    /**
     * Heartbeat from a node to keep it RUNNING
     * POST /naming/membership/heartbeat?nodeName=node1&ip=192.168.1.1
     */
    @PostMapping("/membership/heartbeat")
    public ResponseEntity<MemberStatus> heartbeat(@RequestParam String nodeName,
                                                  @RequestParam(required = false) String ip) {
        MemberStatus status = membershipService.recordHeartbeat(nodeName, ip);
        return ResponseEntity.ok(status);
    }

    /**
     * Inspect current membership runtime states
     * GET /naming/membership/status
     */
    @GetMapping("/membership/status")
    public ResponseEntity<List<MemberStatus>> membershipStatus() {
        List<MemberStatus> list = membershipService.listStatuses();
        return ResponseEntity.ok(list);
    }

    /**
     * Graceful leave by node name.
     * POST /naming/nodes/leave?nodeName=node1
     */
    @PostMapping(value = "/nodes/leave", params = "nodeName")
    public ResponseEntity<String> leaveByName(@RequestParam String nodeName) {
        boolean existed = namingService.leaveNodeByName(nodeName);
        String msg = existed ? "Node left gracefully" : "Node not found (idempotent)";
        return ResponseEntity.ok(msg);
    }

    /**
     * Graceful leave by node ID.
     * POST /naming/nodes/{nodeId}/leave
     */
    @PostMapping("/nodes/{nodeId}/leave")
    public ResponseEntity<String> leaveById(@PathVariable int nodeId) {
        boolean existed = namingService.leaveNodeById(nodeId);
        String msg = existed ? "Node left gracefully" : "Node not found (idempotent)";
        return ResponseEntity.ok(msg);
    }

    /**
     * Remove a node by its name (hash computed internally).
     * DELETE /naming/nodes?nodeName=node1
     */
    @DeleteMapping(value = "/nodes", params = "nodeName")
    public ResponseEntity<Void> removeNodeByName(@RequestParam String nodeName) {
        namingService.removeNode(nodeName);
        return ResponseEntity.noContent().build();
    }

    /**
     * Remove a node by its hash ID.
     * DELETE /naming/nodes/{nodeId}
     */
    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<Void> removeNodeById(@PathVariable int nodeId) {
        namingService.removeNodeById(nodeId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get all registered nodes.
     * GET /naming/nodes
     */
    @GetMapping("/nodes")
    public ResponseEntity<Map<Integer, String>> getAllNodes() {
        Map<Integer, String> nodes = namingService.getAllNodes();
        if (nodes.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(nodes);
    }
}
