package namingserver.ciscos.distlab3.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class Mappingfunction {

    private static Map<Integer, String> nodes = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final File file = new File("nodes.json");

    public Mappingfunction() {
        load();
        save(); //save nodig want dan komt nodes.json terug
    }

    public void addNode(int hash, String ip) {
        nodes.put(hash, ip); // put node in map
        save();
    }

    public void removeNode(int hash) {
        nodes.remove(hash);
        save();
    }

    public static Map<Integer, String> getAllNodes() {
        return nodes;
    }

    //saves it in JSON
    private synchronized void save() { // save de huidige node structuur
        try {
            mapper.writeValue(file, nodes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //this will get loaded when starting up, puts it in nodes
    private synchronized void load() {
        try {
            if (file.exists()) {
                nodes = mapper.readValue(file, new TypeReference<>() {});
                if (!(nodes instanceof ConcurrentHashMap)) {
                    nodes = new ConcurrentHashMap<>(nodes);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}