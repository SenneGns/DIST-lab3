package namingserver.ciscos.distlab3.service;

import namingserver.ciscos.distlab3.model.FileLookupResponse;
import namingserver.ciscos.distlab3.model.NodeInfo;
import namingserver.ciscos.distlab3.repository.Mappingfunction;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NamingService {

    private final HashService hashService;
    private final Mappingfunction nodeRepository;

    // CONSTRUCTOR --------------------------------------------------------------------------
    public NamingService(HashService hashService, Mappingfunction nodeRepository) {
        this.hashService = hashService;
        this.nodeRepository = nodeRepository;
    }

    // METHODS -----------------------------------------------------------------------------
    public NodeInfo registerNode(String nodeName, String ip) {
        int nodeId = hashService.hash(nodeName);

        if (nodeRepository.getAllNodes().containsKey(nodeId)) {
            throw new IllegalArgumentException("Node already exists");
        }

        if (nodeRepository.getAllNodes().containsValue(ip)) {
            throw new IllegalArgumentException("IP address already exists");
        }

        nodeRepository.addNode(nodeId, ip);
        return null;
    }

    public void removeNode(String nodeName) {
        int nodeId = hashService.hash(nodeName);
        nodeRepository.removeNode(nodeId);
    }

    public void removeNodeById(int nodeId) {
        nodeRepository.removeNode(nodeId);
    }

    public Map<Integer, String> getAllNodes() {
        return nodeRepository.getAllNodes();
    }

    public FileLookupResponse findOwner(String fileName) {
        // we kijken of er uberhaupt wel nodes zijn
        if (nodeRepository.getAllNodes().isEmpty()) {
            throw new IllegalStateException("No nodes registered in naming server.");
        }
        // bastandsnaam wordt omgezet naar een hash waarde/getal --> bepaald waar op de ring bestand is/moet komen
        int fileHash = hashService.hash(fileName);
        // we halen alle nodes op
        Map<Integer, String> nodes = nodeRepository.getAllNodes();
        // kern van het algorithme: zoeken onder alle nodehashes naar degene die kleiner zijn dan filehash
        // en pakt hiervan de grootste
        Integer bestHash = null;
        for (Integer nodeHash : nodes.keySet()) {
            if (nodeHash < fileHash) {
                if (bestHash == null || nodeHash > bestHash) {
                    bestHash = nodeHash;
                }
            }
        }
        // als er geen nodehash kleiner is dan de filehash -->("t"erugspringen in de ring")
        // dan kiezen we de grootste nodehash van allemaal
        if (bestHash == null) {
            for (Integer nodeHash : nodes.keySet()) {
                if (bestHash == null || nodeHash > bestHash) {
                    bestHash = nodeHash;
                }
            }
        }
        // we zoeken het IP-adress van die owner
        String ownerIp = nodes.get(bestHash);

        return new FileLookupResponse(fileName, fileHash, bestHash, ownerIp);
    }
}