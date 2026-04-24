package discovery.ciscos.distlab4;

import discovery.ciscos.distlab4.multicast.NodeMulticastListener;
import discovery.ciscos.distlab4.service.*;
import namingserver.ciscos.distlab3.service.HashService;

public class NodeApplication {

    private static final String NAMING_SERVER_URL = "http://localhost:8080";

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

        NodeMulticastListener listener = new NodeMulticastListener(context);
        listener.start();

        BootstrapNode bootstrap = new BootstrapNode(context);
        bootstrap.bootstrap();

        FileTransfer.startReceiver(localFilesPath);

        ReplicationService replication = new ReplicationService(NAMING_SERVER_URL, localFilesPath);
        replication.replicateAllFiles();

        ShutdownHook shutdownHook = new ShutdownHook(NAMING_SERVER_URL, context);
        shutdownHook.register();

        FailureDetector failureDetector = new FailureDetector(NAMING_SERVER_URL, context);
        failureDetector.start();

        System.out.println("[Node] " + nodeName + " actief met ID=" + currentID);
        System.out.println("[Node] previousID=" + context.getPreviousID() + " nextID=" + context.getNextID());

        Thread.currentThread().join();
    }
}