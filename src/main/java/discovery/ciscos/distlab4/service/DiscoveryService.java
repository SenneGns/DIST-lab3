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

    // 2. VOEG DEZE START METHODE TOE
    public void start(String name, String ip) {
        this.myName = name;
        this.myIP = ip;
        this.myID = hash(name); // Zorg dat je een hash-methode hebt!

        // Start de threads die luisteren naar antwoorden en andere nodes
        new Thread(this::listenForMulticast).start(); // Voor Stap 5
        new Thread(this::listenForUnicast).start();    // Voor Stap 6

        // Verstuur je eigen bericht via de methode van je collega
        sendMulticast("BOOTSTRAP:" + myName + ":" + myIP, GROUP, MULTICAST_PORT);
    }

    // 3. STAP 5: LUISTEREN NAAR ANDERE NODES
    private void listenForMulticast() {
        try (MulticastSocket socket = new MulticastSocket(MULTICAST_PORT)) {
            socket.joinGroup(InetAddress.getByName(GROUP));
            while (true) {
                byte[] buf = new byte[512];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength()).trim();

                if (msg.startsWith("BOOTSTRAP:")) {
                    String[] parts = msg.split(":");
                    int newHash = hash(parts[1]);
                    String newIP = parts[2];

                    if (newHash == myID) continue;

                    // Logica PDF p. 13: Ben ik de buur van de nieuwe node?
                    if ((myID < newHash && newHash < nextID) || (myID == nextID)) {
                        this.nextID = newHash;
                        sendUnicast(newIP, "UPDATE_PREVIOUS:" + myID);
                    }
                    if ((previousID < newHash && newHash < myID) || (previousID == myID)) {
                        this.previousID = newHash;
                        sendUnicast(newIP, "UPDATE_NEXT:" + myID);
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 4. STAP 6: LUISTEREN NAAR ANTWOORDEN
    private void listenForUnicast() {
        try (DatagramSocket socket = new DatagramSocket(UNICAST_PORT)) {
            while (true) {
                byte[] buf = new byte[512];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength()).trim();

                if (msg.startsWith("BOOTSTRAP_ACK:")) {
                    int nodes = Integer.parseInt(msg.split(":")[1]);
                    if (nodes == 0) { // Alleen in de ring
                        this.previousID = myID;
                        this.nextID = myID;
                    }
                } else if (msg.startsWith("UPDATE_PREVIOUS:")) {
                    this.previousID = Integer.parseInt(msg.split(":")[1]);
                } else if (msg.startsWith("UPDATE_NEXT:")) {
                    this.nextID = Integer.parseInt(msg.split(":")[1]);
                }
                System.out.println("Status: Prev=" + previousID + " | Next=" + nextID);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Hulpmethode voor Stap 5
    private void sendUnicast(String ip, String msg) {
        try (DatagramSocket s = new DatagramSocket()) {
            byte[] b = msg.getBytes();
            s.send(new DatagramPacket(b, b.length, InetAddress.getByName(ip), UNICAST_PORT));
        } catch (Exception e) { e.printStackTrace(); }
    }

    // De hash methode (moet gelijk zijn aan die van de Naming Server!)
    private int hash(String input) {
        long raw = input.hashCode();
        long MAX = 2147483647L;
        long MIN = -2147483647L;
        int NEW_MAX = 32768;

        double scaled = (raw + MAX) * ((double) NEW_MAX / (MAX + Math.abs(MIN)));
        return (int) Math.round(scaled);
    }


}
