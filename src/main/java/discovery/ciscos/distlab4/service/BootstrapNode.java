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
        try (DatagramSocket neighbourSocket = new DatagramSocket(DiscoveryService.NEIGHBOUR_PORT)) {
            neighbourSocket.setSoTimeout(5000);

            Integer nodesBefore = discovery.sendBootstrapAndAwaitAck(
                    context.getNodeName(),
                    context.getIp(),
                    Duration.ofSeconds(5)
            );

            if (nodesBefore == null) {
                System.out.println("[Bootstrap] Geen ACK ontvangen, veronderstel enige node.");
                return;
            }

            if (nodesBefore < 1) {
                System.out.println("[Bootstrap] Enige node op de ring, previousID = nextID = zichzelf.");
                return;
            }

            awaitNeighbourResponses(neighbourSocket);

        } catch (Exception e) {
            System.out.println("[Bootstrap] Fout tijdens bootstrap: " + e.getMessage());
        }
    }

    private void awaitNeighbourResponses(DatagramSocket socket) {
        long deadline = System.currentTimeMillis() + 5000;

        try {
            while (System.currentTimeMillis() < deadline) {
                byte[] buf = new byte[256];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);

                socket.receive(packet);

                String msg = new String(
                        packet.getData(),
                        packet.getOffset(),
                        packet.getLength(),
                        StandardCharsets.UTF_8
                ).trim();

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

        int previousID = Integer.parseInt(parts[1].trim());
        int nextID = Integer.parseInt(parts[2].trim());

        context.setPreviousID(previousID);
        context.setNextID(nextID);

        System.out.println("[Bootstrap] Buren ingesteld: previousID=" + previousID + " nextID=" + nextID);
    }
}