package namingserver.ciscos.distlab3.service;

import namingserver.ciscos.distlab3.model.FileLookupResponse;
import namingserver.ciscos.distlab3.repository.Mappingfunction;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NamingService {

    private final HashService hashService;
    private final Mappingfunction nodeRepository;

    public NamingService(HashService hashService, Mappingfunction nodeRepository) {
        this.hashService = hashService;
        this.nodeRepository = nodeRepository;
    }

    public void registerNode(String nodeName, String ip) {
        int nodeId = hashService.hash(nodeName);
        nodeRepository.addNode(nodeId, ip);
    }

    public void removeNode(int nodeId) {
        nodeRepository.removeNode(nodeId);
    }

    public Map<Integer, String> getAllNodes() {
        return nodeRepository.getAllNodes();
    }

    public FileLookupResponse findOwner(String fileName) {
        if (Mappingfunction.getAllNodes().isEmpty()) {
            throw new IllegalStateException("No nodes registered in naming server.");
        }

        int fileHash = hashService.hash(fileName);
        Map<Integer, String> nodes = Mappingfunction.getAllNodes();

        Integer bestHash = null;

        for (Integer nodeHash : nodes.keySet()) {
            if (nodeHash < fileHash) {
                if (bestHash == null || nodeHash > bestHash) {
                    bestHash = nodeHash;
                }
            }
        }

        if (bestHash == null) {
            for (Integer nodeHash : nodes.keySet()) {
                if (bestHash == null || nodeHash > bestHash) {
                    bestHash = nodeHash;
                }
            }
        }

        String ownerIp = nodes.get(bestHash);

        return new FileLookupResponse(
                fileName,
                fileHash,
                bestHash,
                ownerIp
        );
    }
}