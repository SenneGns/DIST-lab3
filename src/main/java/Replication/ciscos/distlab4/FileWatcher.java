package Replication.ciscos.distlab4;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class FileWatcher {

    private static final int POLL_INTERVAL_MS = 5000; // elke 5 seconden checken

    private final String localFilesPath;
    private final ReplicationService replicationService;
    private final String namingServerUrl;

    // snapshot van de bestanden die we de vorige keer zagen
    private Set<String> knownFiles = new HashSet<>();

    public FileWatcher(String localFilesPath, ReplicationService replicationService, String namingServerUrl) {
        this.localFilesPath = localFilesPath;
        this.replicationService = replicationService;
        this.namingServerUrl = namingServerUrl.endsWith("/")
                ? namingServerUrl.substring(0, namingServerUrl.length() - 1)
                : namingServerUrl;
    }

    /** Start de achtergrond-thread. Initialiseer de snapshot zodat bestaande bestanden
     *  niet opnieuw als "nieuw" worden beschouwd (die zijn al gerepliceerd bij Starting). */
    public void start() {
        File folder = new File(localFilesPath);
        File[] initial = folder.listFiles();
        if (initial != null) {
            for (File f : initial) {
                if (f.isFile()) knownFiles.add(f.getName());
            }
        }
        Thread t = new Thread(this::watch, "file-watcher");
        t.setDaemon(true);
        t.start();
        System.out.println("[FileWatcher] Gestart, bewaakt: " + localFilesPath);
    }

    // -------------------------------------------------------------------------
    // Achtergrond-loop
    // -------------------------------------------------------------------------

    private void watch() {
        while (true) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
                checkChanges();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void checkChanges() {
        File folder = new File(localFilesPath);
        File[] currentArray = folder.listFiles();
        Set<String> currentFiles = new HashSet<>();
        if (currentArray != null) {
            for (File f : currentArray) {
                if (f.isFile()) currentFiles.add(f.getName());
            }
        }

        // --- Nieuw bestand: repliceer onmiddellijk ---
        for (String name : currentFiles) {
            if (!knownFiles.contains(name)) {
                System.out.println("[FileWatcher] Nieuw bestand: " + name + " – repliceren...");
                replicationService.replicateFile(new File(folder, name));
            }
        }

        // --- Verwijderd bestand: verwijder replica bij owner ---
        for (String name : knownFiles) {
            if (!currentFiles.contains(name)) {
                System.out.println("[FileWatcher] Bestand verwijderd: " + name + " – owner notificeren...");
                notifyOwnerDelete(name);
            }
        }

        knownFiles = currentFiles;
    }

    // -------------------------------------------------------------------------
    // Owner notificeren bij verwijdering
    // -------------------------------------------------------------------------

    /**
     * Vraagt de naming server wie de owner is van dit bestand en stuurt een
     * DELETE-request naar die node om de replica te verwijderen.
     *
     * Vereist dat de node een endpoint heeft: DELETE /node/deleteReplica?filename=X
     */
    private void notifyOwnerDelete(String fileName) {
        try {
            String ownerIp = getOwnerIp(fileName);
            if (ownerIp == null) {
                System.out.println("[FileWatcher] Geen owner gevonden voor: " + fileName);
                return;
            }
            String urlStr = "http://" + ownerIp + ":8080/node/deleteReplica?filename=" + fileName;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            System.out.println("[FileWatcher] Replica verwijderd op " + ownerIp + " -> HTTP " + conn.getResponseCode());
        } catch (Exception e) {
            System.err.println("[FileWatcher] Fout bij notificeren verwijdering van " + fileName + ": " + e.getMessage());
        }
    }

    /**
     * Zelfde logica als ReplicationService.getOwnerIp() – naming server lookup.
     * Gedupliceerd zodat ReplicationService niet verder hoeft aangepast te worden.
     */
    private String getOwnerIp(String fileName) {
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
            System.err.println("[FileWatcher] Fout bij ophalen owner IP: " + e.getMessage());
            return null;
        }
    }
}
