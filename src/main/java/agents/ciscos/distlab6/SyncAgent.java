package agents.ciscos.distlab6;

import Replication.ciscos.distlab4.FileLog;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncAgent implements Runnable, Serializable {

    private static final long serialVersionUID = 1L;

    // Agent's eigen lijst: filename -> locked
    private Map<String, Boolean> agentFileList = new HashMap<>();

    private transient FileLog fileLog;
    private int currentNodeId;

    public SyncAgent() {
    }

    public void setContext(FileLog fileLog, int currentNodeId) {
        this.fileLog = fileLog;
        this.currentNodeId = currentNodeId;
    }

    @Override
    public void run() {
        if (fileLog == null) {
            System.err.println("[SyncAgent] Geen FileLog gekoppeld.");
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
        List<FileLog.LogEntry> entries = fileLog.getEntriesCopy();

        for (FileLog.LogEntry entry : entries) {
            if (!agentFileList.containsKey(entry.fileName)) {
                agentFileList.put(entry.fileName, entry.locked);
                System.out.println("[SyncAgent] Bestand toegevoegd aan agentlijst: " + entry.fileName);
            }

            if (entry.locked) {
                agentFileList.put(entry.fileName, true);
            }

            if (!entry.locked && agentFileList.getOrDefault(entry.fileName, false)) {
                agentFileList.put(entry.fileName, false);
            }
        }

        for (Map.Entry<String, Boolean> agentEntry : agentFileList.entrySet()) {
            fileLog.setLocked(agentEntry.getKey(), agentEntry.getValue());
        }

        System.out.println("[SyncAgent] Sync voltooid op node " + currentNodeId
                + " | files=" + agentFileList.size());
    }

    public Map<String, Boolean> getAgentFileList() {
        return agentFileList;
    }

    public void setAgentFileList(Map<String, Boolean> agentFileList) {
        this.agentFileList = agentFileList;
    }
}