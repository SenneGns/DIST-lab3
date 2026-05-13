package agents.ciscos.distlab6;

import Replication.ciscos.distlab4.FileLog;
import Replication.ciscos.distlab4.FileTransfer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class FailureAgent implements Runnable, Serializable {

    private static final long serialVersionUID = 1L;

    private final int failingNodeId;
    private final int startNodeId;
    private final int newOwnerNodeId;
    private final String newOwnerIp;

    private transient FileLog fileLog;
    private transient String replicaFilesPath;
    private transient int currentNodeId;

    public FailureAgent(int failingNodeId, int startNodeId, int newOwnerNodeId, String newOwnerIp) {
        this.failingNodeId = failingNodeId;
        this.startNodeId = startNodeId;
        this.newOwnerNodeId = newOwnerNodeId;
        this.newOwnerIp = newOwnerIp;
    }

    public void setContext(FileLog fileLog, String replicaFilesPath, int currentNodeId) {
        this.fileLog = fileLog;
        this.replicaFilesPath = replicaFilesPath;
        this.currentNodeId = currentNodeId;
    }

    /** Geeft true als de agent de volledige ring heeft doorlopen. */
    public boolean isDone(int nodeId) {
        return nodeId == startNodeId;
    }

    @Override
    public void run() {
        if (fileLog == null) {
            System.err.println("[FailureAgent] Geen context op node " + currentNodeId);
            return;
        }

        System.out.println("[FailureAgent] Actief op node " + currentNodeId
                + " | gevallen node=" + failingNodeId
                + " | nieuwe eigenaar=" + newOwnerIp);

        for (FileLog.LogEntry entry : fileLog.getEntriesCopy()) {
            if (entry.ownerId != failingNodeId) continue;

            File file = new File(replicaFilesPath, entry.fileName);
            boolean newOwnerHasFile = checkNodeHasFile(newOwnerIp, entry.fileName);

            if (!newOwnerHasFile && file.exists()) {
                // Option 1: bestand overdragen + log updaten
                FileTransfer.sendFile(newOwnerIp, file);
                fileLog.updateOwner(entry.fileName, newOwnerNodeId, newOwnerIp);
                System.out.println("[FailureAgent] Bestand overgedragen: " + entry.fileName + " -> " + newOwnerIp);
            } else {
                // Option 2: alleen log updaten
                fileLog.updateOwner(entry.fileName, newOwnerNodeId, newOwnerIp);
                System.out.println("[FailureAgent] Log bijgewerkt voor: " + entry.fileName);
            }
        }
    }

    /** Stuurt de agent (geserialiseerd) naar het gegeven IP via /node/receiveAgent. */
    public void forwardToNode(String ip) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(this);
            }
            byte[] bytes = baos.toByteArray();

            URL url = new URL("http://" + ip + ":8081/node/receiveAgent");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);
            conn.getOutputStream().write(bytes);
            System.out.println("[FailureAgent] Doorgestuurd naar " + ip + " -> " + conn.getResponseCode());
        } catch (Exception e) {
            System.err.println("[FailureAgent] Fout bij doorsturen: " + e.getMessage());
        }
    }

    private boolean checkNodeHasFile(String ip, String fileName) {
        try {
            String encoded = java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            URL url = new URL("http://" + ip + ":8081/node/hasFile?filename=" + encoded);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public int getFailingNodeId()  { return failingNodeId; }
    public int getStartNodeId()    { return startNodeId; }
    public int getNewOwnerNodeId() { return newOwnerNodeId; }
    public String getNewOwnerIp()  { return newOwnerIp; }
}
