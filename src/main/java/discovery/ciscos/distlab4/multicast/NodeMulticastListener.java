package discovery.ciscos.distlab4.multicast;

import discovery.ciscos.distlab4.service.NodeContext;
import namingserver.ciscos.distlab3.service.HashService;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;

public class NodeMulticastListener {

    private static final String MULTICAST_GROUP = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;
    private static final int UNICAST_REPLY_PORT = 4447;
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
            socket.joinGroup(group);
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
        String message = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
        String[] parts = message.split(":", 3);
        if (parts.length != 3 || !BOOTSTRAP_PREFIX.equals(parts[0])) return;

        String newNodeName = parts[1].trim();
        String newNodeIp = parts[2].trim();

        // eigen bericht negeren
        if (newNodeName.equals(context.getNodeName())) return;

        int newHash = hashService.hash(newNodeName);
        int current = context.getCurrentID();
        int next = context.getNextID();
        int previous = context.getPreviousID();

        if (current < newHash && newHash < next) {
            context.setNextID(newHash);
            sendUnicast(packet.getAddress(), "NEIGHBOUR:" + current + ":" + next);
            System.out.println("[Node] nextID updated to " + newHash);
        } else if (previous < newHash && newHash < current) {
            context.setPreviousID(newHash);
            sendUnicast(packet.getAddress(), "NEIGHBOUR:" + current + ":" + previous);
            System.out.println("[Node] previousID updated to " + newHash);
        }
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
