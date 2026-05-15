package Replication.ciscos.distlab4;

import namingserver.ciscos.distlab3.service.HashService;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class FileTransfer {

    private static final int PORT = 5000;
    private static FileLog fileLog;
    private static String namingServerUrl;
    private static final HashService hashService = new HashService();

    // Makes a TCP connection for sending files, and sends the files
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

    // waits for incoming files and starts receiveFile for each file.
    public static void startReceiver(String saveDirectory, FileLog log, String namingServer) {
        fileLog = log;
        namingServerUrl = namingServer.endsWith("/") ? namingServer.substring(0, namingServer.length() - 1) : namingServer;
        Thread t = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("[FileTransfer] Ontvanger actief op poort " + PORT);
                while (true) {
                    Socket socket = serverSocket.accept();
                    new Thread(() -> receiveFile(socket, saveDirectory)).start();
                }
            } catch (Exception e) {
                System.err.println("[FileTransfer] Fout bij ontvangen: " + e.getMessage());
            }
        }, "file-receiver");
        t.setDaemon(true);
        t.start();
    }

    // receives a file and saves it to the local folder.
    private static void receiveFile(Socket socket, String saveDirectory) {
        try (InputStream in = socket.getInputStream()) {
            DataInputStream dis = new DataInputStream(in);
            String fileName = dis.readUTF();
            long fileSize = dis.readLong();
            String senderIp = socket.getInetAddress().getHostAddress();

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
            System.out.println("[FileTransfer] Bestand ontvangen: " + fileName);

            if (fileLog != null) {
                int fileHash = hashService.hash(fileName);
                int ownerId = lookupOwnerIdByIp(senderIp);
                fileLog.addEntry(fileName, fileHash, senderIp, ownerId);
            }
        } catch (Exception e) {
            System.err.println("[FileTransfer] Fout bij verwerken ontvangen bestand: " + e.getMessage());
        }
    }

    // zoekt het nodeId op van een node op basis van zijn IP via de naming server
    private static int lookupOwnerIdByIp(String ip) {
        if (namingServerUrl == null) return -1;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(namingServerUrl + "/naming/nodes").openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            // response: {"nodeId":"ip", ...} — zoek welk nodeId bij dit IP hoort
            String search = ":\"" + ip + "\"";
            int idx = response.indexOf(search);
            if (idx == -1) return -1;
            int end = idx - 1;
            int start = end - 1;
            while (start > 0 && response.charAt(start - 1) != '"') start--;
            return Integer.parseInt(response.substring(start, end));
        } catch (Exception e) {
            return -1;
        }
    }
}