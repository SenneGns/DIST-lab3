package discovery.ciscos.distlab4.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import Replication.ciscos.distlab4.*;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

public class ShutdownHook {

    private final String namingServerUrl;
    private final NodeContext context;
    private final String replicaFilesPath;
    private final String localFilesPath;

    public ShutdownHook(String namingServerUrl, NodeContext context, String replicaFilesPath, String localFilesPath) {
        this.namingServerUrl = namingServerUrl.endsWith("/") ? namingServerUrl.substring(0, namingServerUrl.length() - 1) : namingServerUrl;
        this.context = context;
        this.replicaFilesPath = replicaFilesPath;
        this.localFilesPath = localFilesPath;
    }

    public void register() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "shutdown-hook"));
    }

    private void shutdown() {
        transferReplicatedFiles();
        notifyOwnersOfLocalFiles();
        notifyPreviousNode();
        notifyNextNode();
        leaveNamingServer();
    }
    /**
     * Transfers all replicated files stored on this node to other available nodes
     * in the ring before shutting down.
     */
    private void transferReplicatedFiles() {
        TreeMap<Integer, String> nodes = getAllNodesMap();
        nodes.remove(context.getCurrentID());// Remove self from the list of targets
        if (nodes.isEmpty()) {
            System.out.println("[Shutdown] Geen andere nodes beschikbaar, bestanden niet overgedragen.");
            return;
        }
        FileLog log = new FileLog(replicaFilesPath);
        for (FileLog.LogEntry entry : log.getEntries()) {
            File file = new File(replicaFilesPath, entry.fileName);
            if (!file.exists()) continue;
            // Find a suitable target node that isn't the original owner of the file
            String targetIp = findTarget(nodes, entry.downloadLocation);
            if (targetIp != null) {
                FileTransfer.sendFile(targetIp, file, entry.downloadLocation);
                System.out.println("[Shutdown] " + entry.fileName + " overgedragen naar " + targetIp);
            }
        }
    }

    /**
     * Shutdown 3/3: waarschuwt de owner (replicated node) van elk lokaal bestand
     * dat deze node stopt. De owner beslist dan of de replica verwijderd of bijgewerkt wordt.
     */
    private void notifyOwnersOfLocalFiles() {
        File localDir = new File(localFilesPath);
        File[] files = localDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (!file.isFile()) continue;
            String ownerIp = getOwnerIp(file.getName());
            if (ownerIp == null) continue;
            // Stuur geen melding naar onszelf
            if (ownerIp.equals(context.getIp())) continue;
            try {
                String url = "http://" + ownerIp + ":8080/node/localFileTerminating"
                        + "?filename=" + URLEncoder.encode(file.getName(), StandardCharsets.UTF_8)
                        + "&sourceIp=" + URLEncoder.encode(context.getIp(), StandardCharsets.UTF_8);
                sendPost(url);
                System.out.println("[Shutdown] Owner " + ownerIp + " gewaarschuwd voor lokaal bestand: " + file.getName());
            } catch (Exception e) {
                System.out.println("[Shutdown] Fout bij waarschuwen owner voor " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private String getOwnerIp(String fileName) {
        try {
            String urlStr = namingServerUrl + "/naming/lookup?filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
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
            System.out.println("[Shutdown] Fout bij ophalen owner IP voor " + fileName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Searches backwards through the ring starting from the previous node ID.
     * Skips nodes if their IP matches the localOwnerIp to ensure redundancy.
     */    private String findTarget(TreeMap<Integer, String> nodes, String localOwnerIp) {
        Integer currentKey = context.getPreviousID();
        for (int i = 0; i < nodes.size(); i++) {
            String ip = nodes.get(currentKey);
            if (ip != null && !ip.equals(localOwnerIp)) return ip;
            Integer key = nodes.lowerKey(currentKey);
            if (key == null) key = nodes.lastKey(); // wrap: kleinste → grootste
            currentKey = key;
        }
        return null; // Occurs if all available nodes already have the file locally
    }

    /**
     * Fetches the current map of all nodes from the naming server.
     */
    private TreeMap<Integer, String> getAllNodesMap() {
        try {
            URL url = new URL(namingServerUrl + "/naming/nodes");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> raw = new ObjectMapper().readValue(response, new TypeReference<>() {});
            TreeMap<Integer, String> result = new TreeMap<>();
            raw.forEach((k, v) -> result.put(Integer.parseInt(k), v));
            return result;
        } catch (Exception e) {
            System.out.println("[Shutdown] Fout bij ophalen nodes: " + e.getMessage());
            return new TreeMap<>();
        }
    }

    /**
     * Informs the previous node in the ring about the new next node.
     */
    private void notifyPreviousNode() {
        try {
            String previousIp = getIpFromNamingServer(context.getPreviousID());
            if (previousIp == null) return;
            String url = "http://" + previousIp + ":8080/node/setNext?nextID=" + context.getNextID();
            sendPost(url);
            System.out.println("[Shutdown] PreviousNode genotificeerd op " + previousIp);
        } catch (Exception e) {
            System.out.println("[Shutdown] Fout bij notificeren previousNode: " + e.getMessage());
        }
    }

    /**
     * Informs the next node in the ring about the new previous node.
     */
    private void notifyNextNode() {
        try {
            String nextIp = getIpFromNamingServer(context.getNextID());
            if (nextIp == null) return;
            String url = "http://" + nextIp + ":8080/node/setPrevious?previousID=" + context.getPreviousID();
            sendPost(url);
            System.out.println("[Shutdown] NextNode genotificeerd op " + nextIp);
        } catch (Exception e) {
            System.out.println("[Shutdown] Fout bij notificeren nextNode: " + e.getMessage());
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
            System.out.println("[Shutdown] Fout bij ophalen IP van naming server: " + e.getMessage());
            return null;
        }
    }

    /**
     * Helper to resolve a Node ID to an IP address via the Naming Server.
     */

    private void leaveNamingServer() {
        try {
            String urlStr = namingServerUrl + "/naming/nodes/leave?nodeName=" +
                    URLEncoder.encode(context.getNodeName(), StandardCharsets.UTF_8);
            sendPost(urlStr);
            System.out.println("[Shutdown] Naming server verlaten.");
        } catch (Exception e) {
            System.out.println("[Shutdown] Fout bij verlaten naming server: " + e.getMessage());
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
        System.out.println("[Shutdown] POST " + urlStr + " -> " + conn.getResponseCode());
    }
}