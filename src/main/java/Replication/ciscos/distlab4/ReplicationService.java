package Replication.ciscos.distlab4;

import namingserver.ciscos.distlab3.service.HashService;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ReplicationService {

    private final String namingServerUrl;
    private final String localFilesPath;
    private final HashService hashService = new HashService();

    public ReplicationService(String namingServerUrl, String localFilesPath) {
        this.namingServerUrl = namingServerUrl.endsWith("/") ? namingServerUrl.substring(0, namingServerUrl.length() - 1) : namingServerUrl;
        this.localFilesPath = localFilesPath;
    }

    // goes through all local files and replicates them one by one.
    public void replicateAllFiles() {
        File folder = new File(localFilesPath);
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("[Replication] Geen lokale bestanden gevonden.");
            return;
        }
        for (File file : files) {
            if (file.isFile()) {
                replicateFile(file);
            }
        }
    }

    // asks the naming server where a file should go and sends it. Also calls for the SendFile function from FileTransfer.
    public void replicateFile(File file) { //nodig voor update dus public
        try {
            String ownerIp = getOwnerIp(file.getName());
            if (ownerIp == null) {
                System.out.println("[Replication] Geen owner gevonden voor: " + file.getName());
                return;
            }
            FileTransfer.sendFile(ownerIp, file);
            System.out.println("[Replication] " + file.getName() + " gerepliceerd naar " + ownerIp);
        } catch (Exception e) {
            System.err.println("[Replication] Fout bij repliceren van " + file.getName() + ": " + e.getMessage());
        }
    }

    // asks the naming server for the IP address of the node that should store a file.
    public String getOwnerIp(String fileName) {
        try {
            String urlStr = namingServerUrl + "/naming/lookup?filename=" + fileName;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String search = "\"ownerIpAddress\":\"";
            int idx = response.indexOf(search);
            if (idx == -1) return null;
            int start = idx + search.length();
            int end = response.indexOf("\"", start);
            return response.substring(start, end);
        } catch (Exception e) {
            System.err.println("[Replication] Fout bij ophalen owner IP: " + e.getMessage());
            return null;
        }
    }

    public void deleteReplica(String fileName) {
        try {
            String ownerIp = getOwnerIp(fileName);
            if (ownerIp == null) {
                System.out.println("[Replication] Geen owner gevonden voor delete: " + fileName);
                return;
            }
            URL url = new URL("http://" + ownerIp + ":8080/node/deleteReplica?filename=" + fileName);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            System.out.println("[Replication] Replica delete " + fileName + " -> " + conn.getResponseCode());
        } catch (Exception e) {
            System.err.println("[Replication] Fout bij deleteReplica: " + e.getMessage());
        }
    }
}
