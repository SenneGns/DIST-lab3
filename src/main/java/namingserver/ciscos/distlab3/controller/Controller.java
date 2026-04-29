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

    public Controller(NamingService namingService) {
        this.namingService = namingService;
    }

    @GetMapping("/lookup")
    public ResponseEntity<?> lookup(@RequestParam String filename) {
        try {
            FileLookupResponse response = namingService.findOwner(filename);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(e.getMessage());
        }
    }

    @PostMapping("/nodes")
    public ResponseEntity<String> registerNode(@RequestParam String nodeName, @RequestParam String ip) {
        try {
            namingService.registerNode(nodeName, ip);
            return ResponseEntity.status(201).body("Node registered successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    @PostMapping(value = "/nodes/leave", params = "nodeName")
    public ResponseEntity<String> leaveByName(@RequestParam String nodeName) {
        boolean existed = namingService.leaveNodeByName(nodeName);
        String msg = existed ? "Node left gracefully" : "Node not found (idempotent)";
        return ResponseEntity.ok(msg);
    }

    @PostMapping("/nodes/{nodeId}/leave")
    public ResponseEntity<String> leaveById(@PathVariable int nodeId) {
        boolean existed = namingService.leaveNodeById(nodeId);
        String msg = existed ? "Node left gracefully" : "Node not found (idempotent)";
        return ResponseEntity.ok(msg);
    }

    @DeleteMapping(value = "/nodes", params = "nodeName")
    public ResponseEntity<Void> removeNodeByName(@RequestParam String nodeName) {
        namingService.removeNode(nodeName);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<Void> removeNodeById(@PathVariable int nodeId) {
        namingService.removeNodeById(nodeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/nodes")
    public ResponseEntity<Map<Integer, String>> getAllNodes() {
        Map<Integer, String> nodes = namingService.getAllNodes();
        if (nodes.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(nodes);
    }
}
