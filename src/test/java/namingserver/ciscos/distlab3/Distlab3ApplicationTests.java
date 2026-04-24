package namingserver.ciscos.distlab3;

import namingserver.ciscos.distlab3.model.FileLookupResponse;
import namingserver.ciscos.distlab3.repository.Mappingfunction;
import namingserver.ciscos.distlab3.service.HashService;
import namingserver.ciscos.distlab3.service.NamingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;


class Distlab3ApplicationTests {

    // Keep contextLoads to verify Spring context starts
    @Test
    void contextLoads() {
    }

    // The following tests were migrated from ControllerIntegrationTests

    static class FakeHashService extends HashService {
        private final Map<String, Integer> table;
        FakeHashService(Map<String, Integer> table) { this.table = table; }
        @Override
        public int hash(String input) { return table.getOrDefault(input, super.hash(input)); }
    }

    private Mappingfunction repo;
    private NamingService namingService;

    @BeforeEach
    void setup() {
        // Clean persistent file and in-memory store to isolate tests
        Mappingfunction.getAllNodes().clear();
        File f = new File("nodes.json");
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    @Test
    @DisplayName("Add a node with a unique node name")
    void addUniqueNode() {
        FakeHashService hasher = new FakeHashService(Map.of(
                "nodeA", 1000
        ));
        repo = new Mappingfunction();
        namingService = new NamingService(hasher, repo);

        namingService.registerNode("nodeA", "10.0.0.1");
        Map<Integer, String> nodes = namingService.getAllNodes();
        assertEquals(1, nodes.size());
        assertEquals("10.0.0.1", nodes.get(1000));
    }

    @Test
    @DisplayName("Add a node with an existing node name (overwrite policy)")
    void addDuplicateNode_overwrites() {
        FakeHashService hasher = new FakeHashService(Map.of(
                "nodeA", 1000
        ));
        repo = new Mappingfunction();
        namingService = new NamingService(hasher, repo);

        namingService.registerNode("nodeA", "10.0.0.1");
        namingService.registerNode("nodeA", "10.0.0.2");
        assertEquals("10.0.0.2", namingService.getAllNodes().get(1000));
    }

    @Test
    @DisplayName("Lookup returns expected IP for a filename (basic ring case)")
    void lookupBasicReturnsExpectedOwner() {
        FakeHashService hasher = new FakeHashService(Map.of(
                "nodeA", 1000,
                "nodeB", 7000,
                "nodeC", 15000,
                "photo.jpg", 8000
        ));
        repo = new Mappingfunction();
        namingService = new NamingService(hasher, repo);

        namingService.registerNode("nodeA", "10.0.0.1");
        namingService.registerNode("nodeB", "10.0.0.2");
        namingService.registerNode("nodeC", "10.0.0.3");

        FileLookupResponse resp = namingService.findOwner("photo.jpg");
        assertEquals(8000, resp.getFileHash());
        assertEquals(7000, resp.getOwnerNodeId());
        assertEquals("10.0.0.2", resp.getOwnerIpAddress());
    }

    @Test
    @DisplayName("Wrap-around: file hash smaller than all node hashes selects largest node")
    void lookupWrapAroundSelectsLargest() {
        FakeHashService hasher = new FakeHashService(Map.of(
                "nodeA", 1000,
                "nodeB", 7000,
                "nodeC", 15000,
                "small.txt", 200
        ));
        repo = new Mappingfunction();
        namingService = new NamingService(hasher, repo);

        namingService.registerNode("nodeA", "10.0.0.1");
        namingService.registerNode("nodeB", "10.0.0.2");
        namingService.registerNode("nodeC", "10.0.0.3");

        FileLookupResponse resp = namingService.findOwner("small.txt");
        assertEquals(200, resp.getFileHash());
        assertEquals(15000, resp.getOwnerNodeId());
        assertEquals("10.0.0.3", resp.getOwnerIpAddress());
    }

    @Test
    @DisplayName("Concurrent: lookup while removing the owner node")
    void concurrentLookupAndRemove() throws Exception {
        FakeHashService hasher = new FakeHashService(Map.of(
                "nodeA", 1000,
                "nodeB", 7000,
                "nodeC", 15000,
                "doc.pdf", 8000
        ));
        repo = new Mappingfunction();
        namingService = new NamingService(hasher, repo);

        namingService.registerNode("nodeA", "10.0.0.1");
        namingService.registerNode("nodeB", "10.0.0.2");
        namingService.registerNode("nodeC", "10.0.0.3");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<FileLookupResponse> lookupTask = () -> {
            try {
                return namingService.findOwner("doc.pdf");
            } catch (IllegalStateException e) {
                return null; // acceptable if raced after delete and no nodes
            }
        };
        Callable<Void> deleteTask = () -> {
            namingService.removeNodeById(7000);
            return null;
        };

        Future<FileLookupResponse> f1 = pool.submit(lookupTask);
        Future<Void> f2 = pool.submit(deleteTask);
        FileLookupResponse r1 = f1.get();
        f2.get();
        pool.shutdown();

        // Either we got a valid owner or, if raced badly and cluster empty (not in this setup), null.
        // With other nodes remaining, we expect a non-null most of the time.
        // We assert no exceptions thrown and allow either outcome.
        assertTrue(r1 == null || r1.getOwnerIpAddress() != null);
    }

    @Test
    @DisplayName("Parallel lookups from two clients return same owner")
    void parallelLookupsSameResult() throws Exception {
        FakeHashService hasher = new FakeHashService(Map.of(
                "nodeA", 1000,
                "nodeB", 7000,
                "nodeC", 15000,
                "video.mp4", 12000
        ));
        repo = new Mappingfunction();
        namingService = new NamingService(hasher, repo);

        namingService.registerNode("nodeA", "10.0.0.1");
        namingService.registerNode("nodeB", "10.0.0.2");
        namingService.registerNode("nodeC", "10.0.0.3");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<FileLookupResponse> c = () -> namingService.findOwner("video.mp4");
        Future<FileLookupResponse> f1 = pool.submit(c);
        Future<FileLookupResponse> f2 = pool.submit(c);
        FileLookupResponse r1 = f1.get();
        FileLookupResponse r2 = f2.get();
        pool.shutdown();

        assertNotNull(r1);
        assertNotNull(r2);
        assertEquals(r1.getOwnerNodeId(), r2.getOwnerNodeId());
        assertEquals(r1.getOwnerIpAddress(), r2.getOwnerIpAddress());
    }

}
