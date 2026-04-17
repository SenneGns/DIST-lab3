package namingserver.ciscos.distlab3.controller;

import namingserver.ciscos.distlab3.model.FileLookupResponse;
import namingserver.ciscos.distlab3.service.NamingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/naming")
public class Controller {

    private final NamingService namingService;

    // CONSTRUCTOR
    public Controller(NamingService namingService) {
        this.namingService = namingService;
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
        namingService.registerNode(nodeName, ip);
        return ResponseEntity.status(201).body("Node registered successfully");
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
