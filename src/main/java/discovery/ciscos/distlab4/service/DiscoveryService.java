package discovery.ciscos.distlab4.service;

import java.time.Duration;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class DiscoveryService {

    // Align with MulticastListenerService on the naming server
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

    //send message to namingserver and nodes
    public void sendMulticast(String message, String groupAddress, int port) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress group = InetAddress.getByName(groupAddress); //geven ze dus mee, geeft groep dus multicast
            byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, port);//dus multicast
            socket.send(packet); //to nodes and server
            System.out.println("[Discovery] Multicast verzonden: " + message + " -> " + groupAddress + ":" + port);
        } catch (Exception e) {
            System.err.println("[Discovery] Fout bij versturen multicast: " + e.getMessage());
        }
    }

    /**
     * Wait for a unicast ACK on ACK_PORT. Returns nodesBefore if received, otherwise null on timeout.
     */
    public Integer awaitBootstrapAck(Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        try (DatagramSocket socket = new DatagramSocket(
                ACK_PORT,
                InetAddress.getByName("0.0.0.0")
        )) {
            socket.setSoTimeout((int) Math.max(1, timeout.toMillis()));
            byte[] buf = new byte[256];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            String resp = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
            System.out.println("[Discovery] ACK ontvangen: " + resp);
            if (resp.startsWith("BOOTSTRAP_ACK:")) {
                String n = resp.substring("BOOTSTRAP_ACK:".length()).trim();
                try { return Integer.parseInt(n); } catch (NumberFormatException ignored) { return null; }
            }
        } catch (Exception e) {
            long remaining = deadline - System.currentTimeMillis();
            System.out.println("[Discovery] Geen ACK binnen timeout (" + timeout.toMillis() + "ms): " + e.getMessage());
        }
        return null;
    }

    public Integer sendBootstrapAndAwaitAck(String nodeName, String ip, Duration timeout) {
        String msg = BOOTSTRAP_PREFIX + ":" + nodeName + ":" + ip;

        try (DatagramSocket socket = new DatagramSocket(
                ACK_PORT,
                InetAddress.getByName("0.0.0.0")
        )) {
            socket.setSoTimeout((int) timeout.toMillis());

            sendMulticast(msg, GROUP, GROUP_PORT);

            byte[] buf = new byte[256];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);

            String resp = new String(
                    packet.getData(),
                    packet.getOffset(),
                    packet.getLength(),
                    StandardCharsets.UTF_8
            ).trim();

            System.out.println("[Discovery] ACK ontvangen: " + resp);

            if (resp.startsWith("BOOTSTRAP_ACK:")) {
                return Integer.parseInt(resp.substring("BOOTSTRAP_ACK:".length()).trim());
            }

        } catch (Exception e) {
            System.out.println("[Discovery] Geen ACK binnen timeout (" + timeout.toMillis() + "ms): " + e.getMessage());
        }

        return null;
    }
}
