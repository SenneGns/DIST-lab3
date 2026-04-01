package namingserver.ciscos.distlab3.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Service
public class Mappingfunction {

    private static Map<Integer, String> nodes = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final File file = new File("nodes.json");

    public Mappingfunction() {
        load();
    }

    public void addNode(int hash, String ip) {
        nodes.put(hash, ip);
        save();
    }

    public void removeNode(int hash) {
        nodes.remove(hash);
        save();
    }

    public static Map<Integer, String> getAllNodes() {
        return nodes;
    }

    private void save() { // save de huidige node structuur
        try {
            mapper.writeValue(file, nodes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void load() {
        try {
            if (file.exists()) {
                nodes = mapper.readValue(file, new TypeReference<>() {});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}