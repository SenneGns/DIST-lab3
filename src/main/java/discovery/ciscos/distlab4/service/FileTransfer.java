package discovery.ciscos.distlab4.service;

import namingserver.ciscos.distlab3.service.HashService;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class FileTransfer {

    private static final int PORT = 5000;
    private static final HashService hashService = new HashService();

    public static void sendFile(String ip, File file) {
        try (Socket socket = new Socket(ip, PORT);
             FileInputStream fis = new FileInputStream(file);
             OutputStream out = socket.getOutputStream()) {

            DataOutputStream dos = new DataOutputStream(out);
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());

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
            log.addEntry(fileName, fileHash, senderIp);

            System.out.println("[FileTransfer] Bestand ontvangen: " + fileName + " van " + senderIp);
        } catch (Exception e) {
            System.err.println("[FileTransfer] Fout bij verwerken ontvangen bestand: " + e.getMessage());
        }
    }
}