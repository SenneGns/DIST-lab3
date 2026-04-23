package discovery.ciscos.distlab4.multicast;

import jakarta.annotation.PostConstruct;
import namingserver.ciscos.distlab3.repository.Mappingfunction;
import namingserver.ciscos.distlab3.service.HashService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;

@Service
public class MulticastListenerService {

    private static final String MULTICAST_GROUP = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;
    private static final int UNICAST_REPLY_PORT = 4447;
    private static final String BOOTSTRAP_PREFIX = "BOOTSTRAP";

    private final HashService hashService;
    private final Mappingfunction nodeRepository;

    public MulticastListenerService(HashService hashService, Mappingfunction nodeRepository) {
        this.hashService = hashService;
        this.nodeRepository = nodeRepository;
    }

    @PostConstruct //Spring calls the methods annotated with @PostConstruct only once, just after the initialization of bean properties.
    public void start() {
        Thread listenerThread = new Thread(this::listen, "multicast-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listen() {
        try (MulticastSocket socket = new MulticastSocket(MULTICAST_PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            socket.setReuseAddress(true);
            socket.joinGroup(group);

            System.out.println("[NS] Multicast listener actief op " + MULTICAST_GROUP + ":" + MULTICAST_PORT);

            while (true) {
                byte[] buffer = new byte[512];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                handlePacket(packet);
            }
        } catch (IOException e) {
            System.err.println("[NS] Multicast listener fout: " + e.getMessage());
        }
    }

    private void handlePacket(DatagramPacket packet) {
        String message = new String(
                packet.getData(),
                packet.getOffset(),
                packet.getLength(),
                StandardCharsets.UTF_8
        ).trim();

        System.out.println("[NS] Ontvangen multicast: " + message);

        String[] parts = message.split(":", 3);
        if (parts.length != 3 || !BOOTSTRAP_PREFIX.equals(parts[0])) {
            System.out.println("[NS] Ongeldig multicastbericht genegeerd.");
            return;
        }

        String nodeName = parts[1].trim();
        String nodeIp = parts[2].trim();

        if (nodeName.isEmpty() || nodeIp.isEmpty()) {
            System.out.println("[NS] Leeg nodeName of nodeIP ontvangen.");
            return;
        }

        handleBootstrap(nodeName, nodeIp, packet.getAddress());
    }

    private void handleBootstrap(String nodeName, String nodeIp, InetAddress senderAddress) {
        TreeMap<Integer, String> before = new TreeMap<>(nodeRepository.getAllNodes());
        int nodesBefore = before.size();
        int nodeHash = hashService.hash(nodeName);

        if (!nodeRepository.getAllNodes().containsKey(nodeHash)) {
            nodeRepository.addNode(nodeHash, nodeIp);
            System.out.println("[NS] Node geregistreerd: " + nodeName + " -> hash=" + nodeHash + ", ip=" + nodeIp);
        } else {
            System.out.println("[NS] Node bestaat al: " + nodeName + " -> hash=" + nodeHash);
        }

        sendBootstrapAck(senderAddress, nodesBefore);

        if (nodesBefore > 0) {
            sendNeighborAcks(nodeHash, senderAddress, before);
        }
    }

    /**
     * Computes which existing nodes are the ring-neighbours of the new node
     * and sends NODE_ACK unicast messages to it so it can set its previousID/nextID.
     *
     * NODE_ACK:NEXT:<senderID>:<oldNext>  → new node sets previousID=senderID, nextID=oldNext
     * NODE_ACK:PREV:<senderID>:<oldPrev>  → new node sets nextID=senderID,     previousID=oldPrev
     */
    private void sendNeighborAcks(int newHash, InetAddress senderAddress, TreeMap<Integer, String> before) {
        // snapshot without the new node
        before.remove(newHash);
        if (before.isEmpty()) return;

        // largest existing hash < newHash (wraps to last if none found)
        Integer prevKey = before.floorKey(newHash - 1);
        if (prevKey == null) prevKey = before.lastKey();

        // smallest existing hash > newHash (wraps to first if none found)
        Integer nextKey = before.ceilingKey(newHash + 1);
        if (nextKey == null) nextKey = before.firstKey();

        // prevKey node had nextKey as its next; now new node sits between them
        sendNodeAck(senderAddress, "NEXT", prevKey, nextKey);

        // nextKey node had prevKey as its prev; now new node sits between them
        if (!nextKey.equals(prevKey)) {
            sendNodeAck(senderAddress, "PREV", nextKey, prevKey);
        }
    }

    private void sendNodeAck(InetAddress receiver, String type, int senderID, int oldNeighbor) {
        String msg = "NODE_ACK:" + type + ":" + senderID + ":" + oldNeighbor;
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(new DatagramPacket(bytes, bytes.length, receiver, UNICAST_REPLY_PORT));
            System.out.println("[NS] NODE_ACK gestuurd: " + msg + " -> " + receiver.getHostAddress());
        } catch (IOException e) {
            System.err.println("[NS] Fout bij versturen NODE_ACK: " + e.getMessage());
        }
    }

    private void sendBootstrapAck(InetAddress receiverAddress, int nodesBefore) {
        String response = "BOOTSTRAP_ACK:" + nodesBefore;
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket socket = new DatagramSocket()) {
            DatagramPacket responsePacket = new DatagramPacket(
                    responseBytes,
                    responseBytes.length,
                    receiverAddress,
                    UNICAST_REPLY_PORT
            );
            socket.send(responsePacket);
            System.out.println("[NS] ACK gestuurd naar " + receiverAddress.getHostAddress() + " -> nodesBefore=" + nodesBefore);
        } catch (IOException e) {
            System.err.println("[NS] Fout bij versturen ACK: " + e.getMessage());
        }
    }
}