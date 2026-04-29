package discovery.ciscos.distlab4.service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class FailureDetector {

    private final String namingServerUrl;
    private final NodeContext context;
    private static final int PING_INTERVAL_MS = 2000;

    public FailureDetector(String namingServerUrl, NodeContext context) {
        this.namingServerUrl = namingServerUrl.endsWith("/") ? namingServerUrl.substring(0, namingServerUrl.length() - 1) : namingServerUrl;
        this.context = context;
    }

    public void start() {
        Thread t = new Thread(this::monitor, "failure-detector");
        t.setDaemon(true);
        t.start();
    }

    private void monitor() {
        while (true) {
            try {
                Thread.sleep(PING_INTERVAL_MS);
                pingNode(context.getPreviousID());
                pingNode(context.getNextID());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void pingNode(int nodeId) {
        if (nodeId == context.getCurrentID()) return;
        try {
            String ip = getIpFromNamingServer(nodeId);
            if (ip == null) return;
            URL url = new URL("http://" + ip + ":8081/node/ping");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int code = conn.getResponseCode();
            System.out.println("[Failure] Ping naar " + ip + " -> " + code);
        } catch (Exception e) {
            System.out.println("[Failure] Node " + nodeId + " niet bereikbaar, failure procedure starten.");
            handleFailure(nodeId);
        }
    }

    private void handleFailure(int failedNodeId) {
        // haal buren op van de gevallen node via naming server
        int newPrevious = getNeighbour(failedNodeId, "previous");
        int newNext = getNeighbour(failedNodeId, "next");

        if (newPrevious == -1 || newNext == -1) {
            System.out.println("[Failure] Kon buren niet ophalen van naming server.");
            return;
        }

        // update buren
        updateNeighbour(newPrevious, "setNext", newNext);
        updateNeighbour(newNext, "setPrevious", newPrevious);

        // verwijder gevallen node uit naming server
        removeFromNamingServer(failedNodeId);

        // update eigen context indien nodig
        if (context.getPreviousID() == failedNodeId) context.setPreviousID(newPrevious);
        if (context.getNextID() == failedNodeId) context.setNextID(newNext);
    }

    private int getNeighbour(int nodeId, String type) {
        try {
            String urlStr = namingServerUrl + "/naming/nodes";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // parse alle node IDs uit de JSON map
            java.util.List<Integer> ids = new java.util.ArrayList<>();
            String[] entries = response.replace("{", "").replace("}", "").replace("\"", "").split(",");
            for (String entry : entries) {
                String[] kv = entry.split(":");
                if (kv.length >= 1) {
                    try { ids.add(Integer.parseInt(kv[0].trim())); } catch (NumberFormatException ignored) {}
                }
            }
            ids.remove((Integer) nodeId);
            if (ids.isEmpty()) return -1;
            ids.sort(Integer::compareTo);

            if (type.equals("previous")) {
                // grootste hash kleiner dan nodeId
                int best = -1;
                for (int id : ids) {
                    if (id < nodeId) best = id;
                }
                return best == -1 ? ids.get(ids.size() - 1) : best;
            } else {
                // kleinste hash groter dan nodeId
                for (int id : ids) {
                    if (id > nodeId) return id;
                }
                return ids.get(0);
            }
        } catch (Exception e) {
            System.out.println("[Failure] Fout bij ophalen buren: " + e.getMessage());
            return -1;
        }
    }

    private void updateNeighbour(int nodeId, String endpoint, int value) {
        try {
            String ip = getIpFromNamingServer(nodeId);
            if (ip == null) return;
            String urlStr = "http://" + ip + ":8081/node/" + endpoint + "?value=" + value;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setDoOutput(true);
            conn.getOutputStream().write(new byte[0]);
            System.out.println("[Failure] " + endpoint + " op " + ip + " -> " + conn.getResponseCode());
        } catch (Exception e) {
            System.out.println("[Failure] Fout bij updaten buur: " + e.getMessage());
        }
    }

    private void removeFromNamingServer(int nodeId) {
        try {
            String urlStr = namingServerUrl + "/naming/nodes/" + nodeId;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            System.out.println("[Failure] Node " + nodeId + " verwijderd -> " + conn.getResponseCode());
        } catch (Exception e) {
            System.out.println("[Failure] Fout bij verwijderen node: " + e.getMessage());
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
            String search = "\"" + nodeId + "\":\"";
            int idx = response.indexOf(search);
            if (idx == -1) return null;
            int start = idx + search.length();
            int end = response.indexOf("\"", start);
            return response.substring(start, end);
        } catch (Exception e) {
            System.out.println("[Failure] Fout bij ophalen IP: " + e.getMessage());
            return null;
        }
    }
}