package discovery.ciscos.distlab4;

import Replication.ciscos.distlab4.FileLog;
import Replication.ciscos.distlab4.FileTransfer;
import Replication.ciscos.distlab4.FileWatcher;
import Replication.ciscos.distlab4.ReplicationService;
import agents.ciscos.distlab6.SyncAgent;
import discovery.ciscos.distlab4.multicast.NodeMulticastListener;
import discovery.ciscos.distlab4.service.*;
import namingserver.ciscos.distlab3.service.HashService;

public class NodeApplication {

    private static final String NAMING_SERVER_URL =
            System.getenv("NAMING_SERVER_URL") != null
                    ? System.getenv("NAMING_SERVER_URL")
                    : "http://localhost:8080";

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 3) {
            System.out.println("Gebruik: NodeApplication <nodeName> <ip> <localFilesPath>");
            return;
        }

        String nodeName = args[0];
        String ip = args[1];
        String localFilesPath = args[2];

        HashService hashService = new HashService();
        int currentID = hashService.hash(nodeName);

        NodeContext context = new NodeContext(nodeName, ip, currentID);

        // HTTP server eerst starten zodat /node/ping al bereikbaar is
        // voordat andere nodes onze bootstrap multicast ontvangen en ons beginnen pingen.
        String replicaFilesPath = localFilesPath + "/replicas";
        new java.io.File(replicaFilesPath).mkdirs();

        FileLog fileLog = new FileLog(replicaFilesPath);
        NodeHttpServer nodeHttpServer = new NodeHttpServer(8080, context, replicaFilesPath, NAMING_SERVER_URL, fileLog, localFilesPath);
        try {
            nodeHttpServer.start();
        } catch (java.io.IOException e) {
            System.err.println("[Node] Fout bij starten HTTP server: " + e.getMessage());
        }

        SyncAgent syncAgent = new SyncAgent();
        syncAgent.setContext(fileLog, currentID, context, NAMING_SERVER_URL);
        nodeHttpServer.setSyncAgent(syncAgent);
        Thread syncThread = new Thread(syncAgent, "sync-agent");
        syncThread.setDaemon(true);
        syncThread.start();

        NodeMulticastListener listener = new NodeMulticastListener(context);
        listener.start();

        BootstrapNode bootstrap = new BootstrapNode(context);
        bootstrap.bootstrap();

        FileTransfer.startReceiver(replicaFilesPath, fileLog, NAMING_SERVER_URL);

        ReplicationService replication = new ReplicationService(NAMING_SERVER_URL, localFilesPath);
        replication.replicateAllFiles();

        FileWatcher fileWatcher = new FileWatcher(localFilesPath, replication, NAMING_SERVER_URL);
        fileWatcher.start();

        ShutdownHook shutdownHook = new ShutdownHook(NAMING_SERVER_URL, context, fileLog, replicaFilesPath);
        shutdownHook.register();

        FailureDetector failureDetector = new FailureDetector(NAMING_SERVER_URL, context);
        failureDetector.start();

        System.out.println("[Node] " + nodeName + " actief met ID=" + currentID);
        System.out.println("[Node] previousID=" + context.getPreviousID() + " nextID=" + context.getNextID());

        // node actief houden
        Thread.currentThread().join();
    }

    private static int hash(String input) {
        long MAX = 2147483647L;
        long MIN = -2147483647L;
        int NEW_MAX = 32768;
        long raw = input.hashCode();
        double scaled = (raw + MAX) * ((double) NEW_MAX / (MAX + Math.abs(MIN)));
        return (int) Math.round(scaled);
    }
}