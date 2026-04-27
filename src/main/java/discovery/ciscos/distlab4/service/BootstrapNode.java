package discovery.ciscos.distlab4.service;

import discovery.ciscos.distlab4.multicast.NodeMulticastListener;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class BootstrapNode {

    private final NodeContext context;
    private final DiscoveryService discovery = new DiscoveryService();

    public BootstrapNode(NodeContext context) {
        this.context = context;
    }

    public void bootstrap() {
        discovery.sendBootstrap(context.getNodeName(), context.getIp());

        Integer nodesBefore = discovery.awaitBootstrapAck(Duration.ofSeconds(5));

        if (nodesBefore == null) {
            System.out.println("[Bootstrap] Geen ACK ontvangen, veronderstel enige node.");
            return;
        }

        if (nodesBefore < 1) {
            System.out.println("[Bootstrap] Enige node op de ring, previousID = nextID = zichzelf.");
            return;
        }

        // er zijn andere nodes, wacht op unicast antwoorden van buren
        awaitNeighbourResponses();
    }

    private void awaitNeighbourResponses() {
        long deadline = System.currentTimeMillis() + 5000;
        try (DatagramSocket socket = new DatagramSocket(DiscoveryService.ACK_PORT)) {
            socket.setSoTimeout(5000);
            while (System.currentTimeMillis() < deadline) {
                byte[] buf = new byte[256];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                System.out.println("[Bootstrap] Ontvangen: " + msg);
                handleNeighbourResponse(msg);
            }
        } catch (Exception e) {
            System.out.println("[Bootstrap] Klaar met wachten op buren: " + e.getMessage());
        }
    }

    private void handleNeighbourResponse(String msg) {
        if (!msg.startsWith("NEIGHBOUR:")) return;
        String[] parts = msg.split(":");
        if (parts.length != 3) return;

        int senderID = Integer.parseInt(parts[1].trim());
        int senderNeighbour = Integer.parseInt(parts[2].trim());

        if (senderID == senderNeighbour) {
            context.setPreviousID(senderID);
            context.setNextID(senderID);
        } else if (senderID < context.getCurrentID()) {
            context.setPreviousID(senderID);
        } else {
            context.setNextID(senderID);
        }
    }
}
