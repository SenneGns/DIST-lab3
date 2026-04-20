package discovery.ciscos.distlab4.multicast;

import namingserver.ciscos.distlab3.repository.Mappingfunction;
import namingserver.ciscos.distlab3.service.HashService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.*;

@Service
public class MulticastListenerService {

    private static final String MULTICAST_GROUP    = "224.0.0.1";
    private static final int    MULTICAST_PORT     = 4446;
    private static final int    UNICAST_REPLY_PORT = 4447;

    private final HashService hashService;
    private final Mappingfunction nodeRepository;

    public MulticastListenerService(HashService hashService, Mappingfunction nodeRepository) {
        this.hashService    = hashService;
        this.nodeRepository = nodeRepository;
    }

    @PostConstruct
    public void start() {
        Thread t = new Thread(this::listen);
        t.setDaemon(true);
        t.setName("multicast-listener");
        t.start();
    }

    private void listen() {
        try (MulticastSocket socket = new MulticastSocket(MULTICAST_PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            socket.joinGroup(group);
            System.out.println("[NS] Multicast listener actief op " + MULTICAST_GROUP + ":" + MULTICAST_PORT);

            byte[] buf = new byte[256];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), 0, packet.getLength()).trim();
                System.out.println("[NS] Ontvangen multicast: " + msg);

                String[] parts = msg.split(":");
                if (parts.length == 3 && parts[0].equals("BOOTSTRAP")) {
                    handleBootstrap(parts[1], parts[2], packet.getAddress());
                }
            }
        } catch (IOException e) {
            System.err.println("[NS] Multicast listener fout: " + e.getMessage());
        }
    }

    private void handleBootstrap(String nodeName, String nodeIP, InetAddress senderAddress) {
        int nodesBefore = nodeRepository.getAllNodes().size();

        int hash = hashService.hash(nodeName);
        nodeRepository.addNode(hash, nodeIP);

        System.out.println("[NS] Node geregistreerd: " + nodeName + " -> hash=" + hash + ", ip=" + nodeIP);
        System.out.println("[NS] Nodes voor registratie: " + nodesBefore);

        String response = "BOOTSTRAP_ACK:" + nodesBefore;
        byte[] responseBytes = response.getBytes();

        try (DatagramSocket unicastSocket = new DatagramSocket()) {
            DatagramPacket responsePacket = new DatagramPacket(
                    responseBytes,
                    responseBytes.length,
                    senderAddress,
                    UNICAST_REPLY_PORT
            );
            unicastSocket.send(responsePacket);
            System.out.println("[NS] ACK gestuurd naar " + senderAddress.getHostAddress() + " -> nodesBefore=" + nodesBefore);
        } catch (IOException e) {
            System.err.println("[NS] Fout bij versturen ACK: " + e.getMessage());
        }
    }
}