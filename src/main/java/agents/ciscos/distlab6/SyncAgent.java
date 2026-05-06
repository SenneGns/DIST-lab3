package agents.ciscos.distlab6;

import Replication.ciscos.distlab4.FileLog;
import discovery.ciscos.distlab4.service.NodeContext;

import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncAgent implements Runnable, Serializable {

    private static final long serialVersionUID = 1L;

    // Agent's eigen lijst: filename -> locked
    private Map<String, Boolean> agentFileList = new HashMap<>();

    private transient FileLog fileLog;
    private transient int currentNodeId;
    private transient NodeContext nodeContext;
    private transient String namingServerUrl;

    public SyncAgent() {
    }

    public void setContext(FileLog fileLog, int currentNodeId, NodeContext nodeContext, String namingServerUrl) {
        this.fileLog = fileLog;
        this.currentNodeId = currentNodeId;
        this.nodeContext = nodeContext;
        this.namingServerUrl = namingServerUrl;
    }

    @Override
    public void run() {
        if (fileLog == null) {
            System.err.println("[SyncAgent] Geen context gekoppeld.");
            return;
        }

        System.out.println("[SyncAgent] gestart op node " + currentNodeId);

        while (true) {
            try {
                syncOnce();
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("[SyncAgent] gestopt op node " + currentNodeId);
                break;
            } catch (Exception e) {
                System.err.println("[SyncAgent] fout: " + e.getMessage());
            }
        }
    }

    private void syncOnce() {
        // Stap 1: lokale bestanden verwerken
        List<FileLog.LogEntry> entries = fileLog.getEntriesCopy();

        for (FileLog.LogEntry entry : entries) {
            if (!agentFileList.containsKey(entry.fileName)) {
                agentFileList.put(entry.fileName, entry.locked);
                System.out.println("[SyncAgent] Bestand toegevoegd aan agentlijst: " + entry.fileName);
            }
            if (entry.locked) {
                agentFileList.put(entry.fileName, true);
            } else if (agentFileList.getOrDefault(entry.fileName, false)) {
                // log zegt ontgrendeld maar agentlijst zegt nog vergrendeld → ontgrendelen
                agentFileList.put(entry.fileName, false);
            }
        }

        // Stap 2: node's lokale filelog bijwerken op basis van agentlijst
        for (Map.Entry<String, Boolean> agentEntry : agentFileList.entrySet()) {
            fileLog.setLocked(agentEntry.getKey(), agentEntry.getValue());
        }

        // Stap 3: synchroniseren met de volgende node
        if (nodeContext != null && namingServerUrl != null) {
            int nextId = nodeContext.getNextID();
            if (nextId != currentNodeId) {
                String nextIp = getNodeIp(nextId);
                if (nextIp != null) {
                    Map<String, Boolean> nextList = getNextNodeSyncList(nextIp);
                    for (Map.Entry<String, Boolean> e : nextList.entrySet()) {
                        // voeg bestanden van volgende node toe aan eigen agentlijst
                        agentFileList.merge(e.getKey(), e.getValue(), (own, next) -> own || next);
                    }
                }
            }
        }

        System.out.println("[SyncAgent] Sync voltooid op node " + currentNodeId
                + " | files=" + agentFileList.size());
    }

    private String getNodeIp(int nodeId) {
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
            return null;
        }
    }

    private Map<String, Boolean> getNextNodeSyncList(String nextIp) {
        Map<String, Boolean> result = new HashMap<>();
        try {
            URL url = new URL("http://" + nextIp + ":8081/agent/syncList");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            if (conn.getResponseCode() != 200) return result;
            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .trim().replace("{", "").replace("}", "").replace("\"", "");
            if (response.isEmpty()) return result;
            for (String pair : response.split(",")) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    result.put(kv[0].trim(), Boolean.parseBoolean(kv[1].trim()));
                }
            }
        } catch (Exception ignored) {
            // volgende node tijdelijk niet bereikbaar
        }
        return result;
    }

    public Map<String, Boolean> getAgentFileList() {
        return agentFileList;
    }

    public void setAgentFileList(Map<String, Boolean> agentFileList) {
        this.agentFileList = agentFileList;
    }
}
