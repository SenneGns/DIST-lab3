package discovery.ciscos.distlab4.service;

import Replication.ciscos.distlab4.FileLog;
import Replication.ciscos.distlab4.FileTransfer;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ShutdownHook {

    private final String namingServerUrl;
    private final NodeContext context;
    private final FileLog fileLog;
    private final String replicaFilesPath;

    public ShutdownHook(String namingServerUrl, NodeContext context, FileLog fileLog, String replicaFilesPath) {
        this.namingServerUrl = namingServerUrl.endsWith("/") ? namingServerUrl.substring(0, namingServerUrl.length() - 1) : namingServerUrl;
        this.context = context;
        this.fileLog = fileLog;
        this.replicaFilesPath = replicaFilesPath;
    }

    public void register() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "shutdown-hook"));
    }

    private void shutdown() {
        transferReplicasToPrevious();
        notifyFileOwners();
        notifyPreviousNode();
        notifyNextNode();
        leaveNamingServer();
    }

    private void transferReplicasToPrevious() {
        String previousIp = getIpFromNamingServer(context.getPreviousID());
        if (previousIp == null) return;
        List<FileLog.LogEntry> entries = fileLog.getEntriesCopy();
        for (FileLog.LogEntry entry : entries) {
            File file = new File(replicaFilesPath, entry.fileName);
            if (!file.exists()) continue;

            String targetIp = previousIp;

            // Edge case: previous node already has this file locally -> fall back one more step
            if (nodeHasFile(previousIp, entry.fileName)) {
                System.err.println("[Shutdown] " + entry.fileName + " al aanwezig op previousNode -> doorsturen naar prev-of-prev");
                int prevOfPrevId = getPreviousIdOfNode(previousIp);
                if (prevOfPrevId != -1) {
                    String prevOfPrevIp = getIpFromNamingServer(prevOfPrevId);
                    if (prevOfPrevIp != null) targetIp = prevOfPrevIp;
                }
            }

            FileTransfer.sendFile(targetIp, file);
            System.err.println("[Shutdown] Replica overgedragen naar " + targetIp + ": " + entry.fileName);
        }
    }

    private boolean nodeHasFile(String ip, String fileName) {
        try {
            String urlStr = "http://" + ip + ":8080/node/hasFile?filename="
                    + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            System.err.println("[Shutdown] Kon hasFile niet controleren op " + ip + ": " + e.getMessage());
            return false;
        }
    }

    private int getPreviousIdOfNode(String ip) {
        try {
            String urlStr = "http://" + ip + ":8080/node/info";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String search = "\"previousID\":";
            int idx = response.indexOf(search);
            if (idx == -1) return -1;
            int start = idx + search.length();
            int end = response.indexOf(",", start);
            if (end == -1) end = response.indexOf("}", start);
            return Integer.parseInt(response.substring(start, end).trim());
        } catch (Exception e) {
            System.err.println("[Shutdown] Kon previousID niet ophalen van " + ip + ": " + e.getMessage());
            return -1;
        }
    }

    private void notifyFileOwners() {
        List<FileLog.LogEntry> entries = fileLog.getEntriesCopy();
        for (FileLog.LogEntry entry : entries) {
            if (entry.downloadLocation == null || entry.downloadLocation.isEmpty()) continue;
            try {
                String encoded = URLEncoder.encode(entry.fileName, StandardCharsets.UTF_8);
                String sourceEncoded = URLEncoder.encode(context.getIp(), StandardCharsets.UTF_8);
                String url = "http://" + entry.downloadLocation + ":8080/node/localFileTerminating"
                        + "?filename=" + encoded + "&sourceIp=" + sourceEncoded;
                sendPost(url);
                System.err.println("[Shutdown] Owner genotificeerd: " + entry.downloadLocation + " voor " + entry.fileName);
            } catch (Exception e) {
                System.err.println("[Shutdown] Fout bij notificeren owner: " + e.getMessage());
            }
        }
    }

    // stuur nextID naar vorige buur zodat die zijn nextID kan updaten
    private void notifyPreviousNode() {
        try {
            String previousIp = getIpFromNamingServer(context.getPreviousID());
            if (previousIp == null) return;
            String url = "http://" + previousIp + ":8080/node/setNext?nextID=" + context.getNextID();
            sendPost(url);
            System.err.println("[Shutdown] PreviousNode genotificeerd op " + previousIp);
        } catch (Exception e) {
            System.err.println("[Shutdown] Fout bij notificeren previousNode: " + e.getMessage());
        }
    }

    // stuur previousID naar volgende buur zodat die zijn previousID kan updaten
    private void notifyNextNode() {
        try {
            String nextIp = getIpFromNamingServer(context.getNextID());
            if (nextIp == null) return;
            String url = "http://" + nextIp + ":8080/node/setPrevious?previousID=" + context.getPreviousID();
            sendPost(url);
            System.err.println("[Shutdown] NextNode genotificeerd op " + nextIp);
        } catch (Exception e) {
            System.err.println("[Shutdown] Fout bij notificeren nextNode: " + e.getMessage());
        }
    }

    private String getIpFromNamingServer(int nodeId) {
        try {
            String urlStr = namingServerUrl + "/naming/nodes";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            // response is JSON map: {"hash":"ip", ...}
            String search = "\"" + nodeId + "\":\"";
            int idx = response.indexOf(search);
            if (idx == -1) return null;
            int start = idx + search.length();
            int end = response.indexOf("\"", start);
            return response.substring(start, end);
        } catch (Exception e) {
            System.err.println("[Shutdown] Fout bij ophalen IP van naming server: " + e.getMessage());
            return null;
        }
    }

    private void leaveNamingServer() {
        try {
            String urlStr = namingServerUrl + "/naming/nodes/leave?nodeName=" +
                    URLEncoder.encode(context.getNodeName(), StandardCharsets.UTF_8);
            sendPost(urlStr);
            System.err.println("[Shutdown] Naming server verlaten.");
        } catch (Exception e) {
            System.err.println("[Shutdown] Fout bij verlaten naming server: " + e.getMessage());
        }
    }

    private void sendPost(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        conn.setDoOutput(true);
        conn.getOutputStream().write(new byte[0]);
        System.err.println("[Shutdown] POST " + urlStr + " -> " + conn.getResponseCode());
    }
}