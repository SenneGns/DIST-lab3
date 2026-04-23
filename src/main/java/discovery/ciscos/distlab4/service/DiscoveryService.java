package discovery.ciscos.distlab4.service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class DiscoveryService {

    // Align with MulticastListenerService on the naming server
    public static final String GROUP = "230.0.0.1";
    public static final int GROUP_PORT = 4446;
    public static final int ACK_PORT = 4447; // unicast reply expected here
    public static final String BOOTSTRAP_PREFIX = "BOOTSTRAP";

    /**
     * Send a bootstrap announcement using the agreed format: BOOTSTRAP:<name>:<ip>
     */
    public void sendBootstrap(String nodeName, String ip) {
        String msg = BOOTSTRAP_PREFIX + ":" + nodeName + ":" + ip;
        sendMulticast(msg, GROUP, GROUP_PORT);
    }

    public void sendMulticast(String message, String groupAddress, int port) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress group = InetAddress.getByName(groupAddress);
            byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, port);
            socket.send(packet);
            System.out.println("[Discovery] Multicast verzonden: " + message + " -> " + groupAddress + ":" + port);
        } catch (Exception e) {
            System.err.println("[Discovery] Fout bij versturen multicast: " + e.getMessage());
        }
    }

    /**
     * Listens on ACK_PORT for the duration of the timeout, collecting:
     *  - BOOTSTRAP_ACK:<nodesBefore>   from the naming server
     *  - NODE_ACK:NEXT:<senderID>:<oldNext>  → previousID = senderID, nextID = oldNext
     *  - NODE_ACK:PREV:<senderID>:<oldPrev>  → nextID = senderID, previousID = oldPrev
     *
     * Returns a configured RingState, or null if no BOOTSTRAP_ACK was received.
     */
    public RingState awaitBootstrapAck(int currentID, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        Integer nodesBefore = null;
        Integer previousID = null;
        Integer nextID = null;

        try (DatagramSocket socket = new DatagramSocket(ACK_PORT)) {
            while (System.currentTimeMillis() < deadline) {
                int remaining = (int) (deadline - System.currentTimeMillis());
                if (remaining <= 0) break;
                socket.setSoTimeout(remaining);

                byte[] buf = new byte[256];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                } catch (Exception e) {
                    break; // timeout
                }

                String resp = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                System.out.println("[Discovery] Ontvangen: " + resp);

                if (resp.startsWith("BOOTSTRAP_ACK:")) {
                    try {
                        nodesBefore = Integer.parseInt(resp.substring("BOOTSTRAP_ACK:".length()).trim());
                    } catch (NumberFormatException ignored) {}

                } else if (resp.startsWith("NODE_ACK:NEXT:")) {
                    // NODE_ACK:NEXT:<senderID>:<oldNext>
                    // senderID is our previousID, oldNext is our nextID
                    String[] parts = resp.split(":");
                    if (parts.length == 4) {
                        previousID = Integer.parseInt(parts[2]);
                        nextID     = Integer.parseInt(parts[3]);
                    }

                } else if (resp.startsWith("NODE_ACK:PREV:")) {
                    // NODE_ACK:PREV:<senderID>:<oldPrev>
                    // senderID is our nextID, oldPrev is our previousID
                    String[] parts = resp.split(":");
                    if (parts.length == 4) {
                        nextID     = Integer.parseInt(parts[2]);
                        previousID = Integer.parseInt(parts[3]);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[Discovery] Socket fout: " + e.getMessage());
        }

        if (nodesBefore == null) {
            System.out.println("[Discovery] Geen BOOTSTRAP_ACK ontvangen binnen timeout.");
            return null;
        }

        RingState state = new RingState(currentID);

        if (nodesBefore < 1) {
            // enige node in het netwerk: prev = next = zichzelf
            System.out.println("[Discovery] Enige node in netwerk. prev=next=currentID=" + currentID);
        } else {
            // gebruik ontvangen neighbour info
            if (previousID != null) state.setPreviousID(previousID);
            if (nextID != null)     state.setNextID(nextID);
            System.out.println("[Discovery] Ring state ingesteld: " + state);
        }

        return state;
    }
}