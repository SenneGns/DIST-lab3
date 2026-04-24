package Replication.ciscos.distlab4;

import namingserver.ciscos.distlab3.service.HashService;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class FileTransfer {

    private static final int PORT = 5000;
    private static final HashService hashService = new HashService();

    public static void sendFile(String ip, File file) {
        sendFile(ip, file, "");
    }

    // originalOwnerIp: bewaar originele downloadLocation (leeg = gebruik senderIp bij ontvanger)
    public static void sendFile(String ip, File file, String originalOwnerIp) {
        try (Socket socket = new Socket(ip, PORT);
             FileInputStream fis = new FileInputStream(file);
             OutputStream out = socket.getOutputStream()) {

            DataOutputStream dos = new DataOutputStream(out);
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());
            dos.writeUTF(originalOwnerIp == null ? "" : originalOwnerIp);

            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            System.out.println("[FileTransfer] Bestand verstuurd: " + file.getName() + " naar " + ip);
        } catch (Exception e) {
            System.err.println("[FileTransfer] Fout bij versturen: " + e.getMessage());
        }
    }

    public static void startReceiver(String saveDirectory) {
        Thread t = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("[FileTransfer] Ontvanger actief op poort " + PORT);
                while (true) {
                    Socket socket = serverSocket.accept();
                    String remoteIp = socket.getInetAddress().getHostAddress();
                    new Thread(() -> receiveFile(socket, saveDirectory, remoteIp)).start();
                }
            } catch (Exception e) {
                System.err.println("[FileTransfer] Fout bij ontvangen: " + e.getMessage());
            }
        }, "file-receiver");
        t.setDaemon(true);
        t.start();
    }

    private static void receiveFile(Socket socket, String saveDirectory, String senderIp) {
        try (InputStream in = socket.getInputStream()) {
            DataInputStream dis = new DataInputStream(in);
            String fileName = dis.readUTF();
            long fileSize = dis.readLong();
            String originalOwnerIp = dis.readUTF();
            String downloadLocation = originalOwnerIp.isEmpty() ? senderIp : originalOwnerIp;

            File outFile = new File(saveDirectory, fileName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[4096];
                long remaining = fileSize;
                int read;
                while (remaining > 0 && (read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                    fos.write(buffer, 0, read);
                    remaining -= read;
                }
            }

            // log aanmaken als owner
            int fileHash = hashService.hash(fileName);
            FileLog log = new FileLog(saveDirectory);
            log.removeEntry(fileName); // verwijder eventuele oude entry
            log.addEntry(fileName, fileHash, downloadLocation);

            System.out.println("[FileTransfer] Bestand ontvangen: " + fileName + " van " + senderIp + " (owner: " + downloadLocation + ")");
        } catch (Exception e) {
            System.err.println("[FileTransfer] Fout bij verwerken ontvangen bestand: " + e.getMessage());
        }
    }
}