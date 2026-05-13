package discovery.ciscos.distlab4.multicast;

import discovery.ciscos.distlab4.service.NodeContext;
import namingserver.ciscos.distlab3.service.HashService;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.net.NetworkInterface;
import java.net.InetSocketAddress;

public class NodeMulticastListener {

    private static final String MULTICAST_GROUP = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;
    private static final int UNICAST_REPLY_PORT = 4448;
    private static final String BOOTSTRAP_PREFIX = "BOOTSTRAP";

    private final NodeContext context;
    private final HashService hashService;

    public NodeMulticastListener(NodeContext context) {
        this.context = context;
        this.hashService = new HashService(); //eig beter dan multicastlistenerservice
    }

    public void start() {
        Thread t = new Thread(this::listen, "node-multicast-listener");
        t.setDaemon(true);
        t.start();
    }

    private void listen() {
        try (MulticastSocket socket = new MulticastSocket(MULTICAST_PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            socket.setReuseAddress(true);

            // Expliciet eth0 opgeven zodat Docker de juiste interface gebruikt
            NetworkInterface ni = NetworkInterface.getByName("eth0");
            if (ni != null) {
                socket.joinGroup(new InetSocketAddress(group, MULTICAST_PORT), ni);
                System.out.println("[Node] Multicast joined op eth0");
            } else {
                socket.joinGroup(group);
                System.out.println("[Node] Multicast joined op standaard interface");
            }

            System.out.println("[Node] Multicast listener actief");
            while (true) {
                byte[] buffer = new byte[512];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                handlePacket(packet);
            }
        } catch (IOException e) {
            System.err.println("[Node] Multicast listener fout: " + e.getMessage());
        }
    }

    private void handlePacket(DatagramPacket packet) {
        String message = new String(
                packet.getData(),
                packet.getOffset(),
                packet.getLength(),
                StandardCharsets.UTF_8
        ).trim();

        String[] parts = message.split(":", 3);
        if (parts.length != 3 || !BOOTSTRAP_PREFIX.equals(parts[0])) return;

        String newNodeName = parts[1].trim();
        String newNodeIp = parts[2].trim();

        if (newNodeName.equals(context.getNodeName())) return;

        int newHash = hashService.hash(newNodeName);

        int current = context.getCurrentID();
        int next = context.getNextID();
        int previous = context.getPreviousID();

        // Special case: er is maar 1 node op de ring
        if (previous == current && next == current) {
            context.setPreviousID(newHash);
            context.setNextID(newHash);

            sendUnicast(packet.getAddress(), "NEIGHBOUR:" + current + ":" + current);

            System.out.println("[Node] Enige node op ring, previousID en nextID updated naar " + newHash);
            return;
        }

        // Nieuwe node zit tussen deze node en zijn next => deze node wordt previous van nieuwe node
        if (isBetween(current, newHash, next)) {
            context.setNextID(newHash);

            // vanuit perspectief van nieuwe node: previous = current, next = oude next
            sendUnicast(packet.getAddress(), "NEIGHBOUR:" + current + ":" + next);

            System.out.println("[Node] nextID updated to " + newHash);
            return;
        }

        // Nieuwe node zit tussen previous en deze node => deze node wordt next van nieuwe node
        if (isBetween(previous, newHash, current)) {
            context.setPreviousID(newHash);

            // vanuit perspectief van nieuwe node: previous = oude previous, next = current
            sendUnicast(packet.getAddress(), "NEIGHBOUR:" + previous + ":" + current);

            System.out.println("[Node] previousID updated to " + newHash);
        }
    }

    private boolean isBetween(int start, int value, int end) {
        if (start < end) {
            return start < value && value < end;
        }

        // wrap-around, bv start=30000, end=1000
        if (start > end) {
            return value > start || value < end;
        }

        // start == end betekent normaal één node-ring;
        // die case wordt hierboven apart behandeld
        return false;
    }

    private void sendUnicast(InetAddress receiver, String message) {
        byte[] buf = message.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length, receiver, UNICAST_REPLY_PORT);
            socket.send(packet);
            System.out.println("[Node] Unicast gestuurd: " + message);
        } catch (IOException e) {
            System.err.println("[Node] Fout bij versturen unicast: " + e.getMessage());
        }
    }
}