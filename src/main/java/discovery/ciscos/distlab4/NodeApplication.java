package discovery.ciscos.distlab4;

import Replication.ciscos.distlab4.*;
import discovery.ciscos.distlab4.service.FailureDetector;
import Replication.ciscos.distlab4.FileTransfer;
import discovery.ciscos.distlab4.multicast.NodeMulticastListener;
import discovery.ciscos.distlab4.service.*;
import namingserver.ciscos.distlab3.service.HashService;

import java.io.File;

public class NodeApplication {

    private static final String NAMING_SERVER_URL = "http://localhost:8080";

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Gebruik: NodeApplication <nodeName> <ip> <localFilesPath>");
            return;
        }
        String nodeName = args[0];
        String ip = args[1];
        String localFilesPath = args[2] + "/local";
        String replicaFilesPath = args[2] + "/replicas";

        new File(localFilesPath).mkdirs();
        new File(replicaFilesPath).mkdirs();

        HashService hashService = new HashService();
        int currentID = hashService.hash(nodeName);

        NodeContext context = new NodeContext(nodeName, ip, currentID);

        NodeMulticastListener listener = new NodeMulticastListener(context);
        listener.start();

        BootstrapNode bootstrap = new BootstrapNode(context);
        bootstrap.bootstrap();

        NodeHttpServer httpServer = new NodeHttpServer(8081, context, replicaFilesPath);
        httpServer.start();

        FileTransfer.startReceiver(replicaFilesPath);
        ReplicationService replication = new ReplicationService(NAMING_SERVER_URL, localFilesPath);
        replication.replicateAllFiles();

        FileWatcher fileWatcher = new FileWatcher(localFilesPath, replication, NAMING_SERVER_URL);
        fileWatcher.start();

        ShutdownHook shutdownHook = new ShutdownHook(NAMING_SERVER_URL, context, replicaFilesPath, localFilesPath);
        shutdownHook.register();

        FailureDetector failureDetector = new FailureDetector(NAMING_SERVER_URL, context);
        failureDetector.start();

        System.out.println("[Node] " + nodeName + " actief met ID=" + currentID);
        System.out.println("[Node] previousID=" + context.getPreviousID() + " nextID=" + context.getNextID());

        // node actief houden
        Thread.currentThread().join();
    }

}