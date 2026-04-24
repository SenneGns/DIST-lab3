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

    public ShutdownHook(String namingServerUrl, NodeContext context, String replicaFilesPath) {
        this.namingServerUrl = namingServerUrl.endsWith("/") ? namingServerUrl.substring(0, namingServerUrl.length() - 1) : namingServerUrl;
        this.context = context;
        this.replicaFilesPath = replicaFilesPath;
    }

    public void register() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "shutdown-hook"));
    }

    private void shutdown() {
        transferReplicatedFiles();
        notifyPreviousNode();
        notifyNextNode();
        leaveNamingServer();
    }

    private void transferReplicatedFiles() {
        TreeMap<Integer, String> nodes = getAllNodesMap();
        nodes.remove(context.getCurrentID());
        if (nodes.isEmpty()) {
            System.out.println("[Shutdown] Geen andere nodes beschikbaar, bestanden niet overgedragen.");
            return;
        }
        FileLog log = new FileLog(replicaFilesPath);
        for (FileLog.LogEntry entry : log.getEntries()) {
            File file = new File(replicaFilesPath, entry.fileName);
            if (!file.exists()) continue;
            String targetIp = findTarget(nodes, entry.downloadLocation);
            if (targetIp != null) {
                FileTransfer.sendFile(targetIp, file, entry.downloadLocation);
                System.out.println("[Shutdown] " + entry.fileName + " overgedragen naar " + targetIp);
            }
        }
    }

    // Loopt terug door de ring vanaf previousID, slaat nodes over waarvan de IP gelijk is aan localOwnerIp.
    private String findTarget(TreeMap<Integer, String> nodes, String localOwnerIp) {
        Integer currentKey = context.getPreviousID();
        for (int i = 0; i < nodes.size(); i++) {
            String ip = nodes.get(currentKey);
            if (ip != null && !ip.equals(localOwnerIp)) return ip;
            Integer key = nodes.lowerKey(currentKey);
                        if (key == null) key = nodes.lastKey(); // wrap: kleinste → grootste
            currentKey = key;
        }
        return null; // alle nodes hebben het bestand lokaal (zou niet mogen voorkomen)
    }

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

    // stuur nextID naar vorige buur zodat die zijn nextID kan updaten
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

    // stuur previousID naar volgende buur zodat die zijn previousID kan updaten
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