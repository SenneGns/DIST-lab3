package discovery.ciscos.distlab4.service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class DiscoveryService {
    private int myID, previousID, nextID;
    private String myName, myIP;
    private static final int MULTICAST_PORT = 4446;
    private static final int UNICAST_PORT = 4447;
    private static final String GROUP = "230.0.0.1";

    public void sendMulticast(String message, String groupAddress, int port) {
        // Gebruik een try-with-resources om de socket automatisch te sluiten
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress group = InetAddress.getByName(groupAddress);
            byte[] buffer = message.getBytes();

            // Maak het pakket aan gericht aan de multicast groep
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, port);

            // Verstuur het bericht
            socket.send(packet);
            System.out.println("Multicast bericht verzonden: " + message);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
