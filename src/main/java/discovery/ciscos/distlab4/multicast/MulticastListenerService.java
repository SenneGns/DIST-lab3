package discovery.ciscos.distlab4.multicast;

import jakarta.annotation.PostConstruct;
import namingserver.ciscos.distlab3.repository.Mappingfunction;
import namingserver.ciscos.distlab3.service.HashService;
import namingserver.ciscos.distlab3.service.MembershipService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;

@Service
public class MulticastListenerService {

    private static final String MULTICAST_GROUP = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;
    private static final int UNICAST_REPLY_PORT = 4447;
    private static final String BOOTSTRAP_PREFIX = "BOOTSTRAP";

    private final HashService hashService;
    private final Mappingfunction nodeRepository;
    private final MembershipService membershipService;

    public MulticastListenerService(HashService hashService, Mappingfunction nodeRepository, MembershipService membershipService) {
        this.hashService = hashService;
        this.nodeRepository = nodeRepository;
        this.membershipService = membershipService;
    }

    // Start de listener op een aparte thread zodra Spring de service initialiseert
    @PostConstruct //Spring calls the methods annotated with @PostConstruct only once, just after the initialization of bean properties.
    public void start() {
        Thread listenerThread = new Thread(this::listen, "multicast-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    // Luistert continu op de multicastgroep voor bootstrapberichten van nodes
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

    // Verwerkt een via multicast ontvangen UDP-pakket en controleert of het een geldig bootstrapbericht bevat.
    // want is eigenlijk ook udp die multicast
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

    // Registreert de node en zet ze meteen als actief in de membershipstatus
    private void handleBootstrap(String nodeName, String nodeIp, InetAddress senderAddress) { //sender address = van wie het kwam
        int nodesBefore = nodeRepository.getAllNodes().size();
        int nodeHash = hashService.hash(nodeName);

        if (!nodeRepository.getAllNodes().containsKey(nodeHash)) {
            nodeRepository.addNode(nodeHash, nodeIp);
            membershipService.recordHeartbeat(nodeName, nodeIp);
            System.out.println("[NS] Node geregistreerd: " + nodeName + " -> hash=" + nodeHash + ", ip=" + nodeIp);
        } else {
            System.out.println("[NS] Node bestaat al: " + nodeName + " -> hash=" + nodeHash);
        }
        sendBootstrapAck(senderAddress, nodesBefore);
    }

    // Stuurt een unicast bevestiging terug met het aantal nodes dat al geregistreerd was
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
