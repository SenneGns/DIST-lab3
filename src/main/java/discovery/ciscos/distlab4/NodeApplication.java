package discovery.ciscos.distlab4;

import discovery.ciscos.distlab4.multicast.NodeMulticastListener;
import discovery.ciscos.distlab4.service.BootstrapNode;
import discovery.ciscos.distlab4.service.FailureDetector;
import discovery.ciscos.distlab4.service.NodeContext;
import discovery.ciscos.distlab4.service.ShutdownHook;

public class NodeApplication {
    // Zorgt ervoor da alles goed opstart bij nieuwe node
    private static final String NAMING_SERVER_URL = "http://localhost:8080";

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 2) {
            System.out.println("Gebruik: NodeApplication <nodeName> <ip>");
            return;
        }

        String nodeName = args[0];
        String ip = args[1];

        // hash berekenen
        int currentID = hash(nodeName);

        // context aanmaken
        NodeContext context = new NodeContext(nodeName, ip, currentID);

        // multicast listener starten zodat bestaande nodes ons kunnen vinden
        NodeMulticastListener listener = new NodeMulticastListener(context);
        listener.start();

        // bootstrap uitvoeren
        BootstrapNode bootstrap = new BootstrapNode(context);
        bootstrap.bootstrap();

        // shutdown hook registreren
        ShutdownHook shutdownHook = new ShutdownHook(NAMING_SERVER_URL, context);
        shutdownHook.register();

        // failure detector starten
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