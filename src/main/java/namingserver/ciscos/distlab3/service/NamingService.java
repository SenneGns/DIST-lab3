package namingserver.ciscos.distlab3.service;

import namingserver.ciscos.distlab3.model.FileLookupResponse;
import namingserver.ciscos.distlab3.model.NodeInfo;
import namingserver.ciscos.distlab3.repository.NodeRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;

@Service
public class NamingService {

    private final HashService hashService;
    private final NodeRepository nodeRepository;

    public NamingService(HashService hashService, NodeRepository nodeRepository) {
        this.hashService = hashService;
        this.nodeRepository = nodeRepository;
    }

    public NodeInfo registerNode(String nodeName, String ip) {
        int nodeId = hashService.hash(nodeName);

        NodeInfo node = new NodeInfo(nodeId, nodeName, ip);
        nodeRepository.save(node);

        return node;
    }

    public void removeNode(int nodeId) {
        nodeRepository.deleteById(nodeId);
    }

    public Collection<NodeInfo> getAllNodes() {
        return nodeRepository.findAll();
    }

    public FileLookupResponse findOwner(String fileName) {
        if (nodeRepository.isEmpty()) {
            throw new IllegalStateException("No nodes registered in naming server.");
        }

        int fileHash = hashService.hash(fileName);
        NavigableMap<Integer, NodeInfo> nodes = nodeRepository.getNodes();

        Map.Entry<Integer, NodeInfo> ownerEntry = nodes.lowerEntry(fileHash);

        if (ownerEntry == null) {
            ownerEntry = nodes.lastEntry();
        }

        NodeInfo owner = ownerEntry.getValue();

        return new FileLookupResponse(
                fileName,
                fileHash,
                owner.getId(),
                owner.getIpAddress()
        );
    }
}