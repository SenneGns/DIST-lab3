package discovery.ciscos.distlab4.service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class DiscoveryService {

    public static final String GROUP = "230.0.0.1";
    public static final int GROUP_PORT = 4446;
    public static final int ACK_PORT = 4447;
    public static final int NEIGHBOUR_PORT = 4448;
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
     * Opens ACK socket FIRST, then sends bootstrap, then waits for ACK.
     * This prevents the race condition where ACK arrives before socket is open.
     */
    public Integer sendBootstrapAndAwaitAck(String nodeName, String ip, Duration timeout) {
        try (DatagramSocket socket = new DatagramSocket(ACK_PORT, InetAddress.getByName("0.0.0.0"))) {
            socket.setSoTimeout((int) timeout.toMillis());
            System.out.println("[Discovery] Luistert op poort " + ACK_PORT + " interface: " + socket.getLocalAddress());

            // Socket is open, nu pas multicast sturen
            sendBootstrap(nodeName, ip);

            // Wachten op ACK
            byte[] buf = new byte[256];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            String resp = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
            System.out.println("[Discovery] ACK ontvangen: " + resp);

            if (resp.startsWith("BOOTSTRAP_ACK:")) {
                String n = resp.substring("BOOTSTRAP_ACK:".length()).trim();
                try {
                    return Integer.parseInt(n);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        } catch (Exception e) {
            System.out.println("[Discovery] Geen ACK binnen timeout (" + timeout.toMillis() + "ms): " + e.getMessage());
        }
        return null;
    }

    /**
     * Wait for a unicast ACK on ACK_PORT. Returns nodesBefore if received, otherwise null on timeout.
     */
    public Integer awaitBootstrapAck(Duration timeout) {
        try (DatagramSocket socket = new DatagramSocket(ACK_PORT, InetAddress.getByName("0.0.0.0"))) {
            socket.setSoTimeout((int) Math.max(1, timeout.toMillis()));
            System.out.println("[Discovery] Luistert op poort " + ACK_PORT + " interface: " + socket.getLocalAddress());
            byte[] buf = new byte[256];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            String resp = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
            System.out.println("[Discovery] ACK ontvangen: " + resp);
            if (resp.startsWith("BOOTSTRAP_ACK:")) {
                String n = resp.substring("BOOTSTRAP_ACK:".length()).trim();
                try {
                    return Integer.parseInt(n);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        } catch (Exception e) {
            System.out.println("[Discovery] Geen ACK binnen timeout (" + timeout.toMillis() + "ms): " + e.getMessage());
        }
        return null;
    }
}