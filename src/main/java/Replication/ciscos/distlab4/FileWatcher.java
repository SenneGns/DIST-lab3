package Replication.ciscos.distlab4;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class FileWatcher {

    private static final int POLL_INTERVAL_MS = 5000; // elke 5 seconden checken

    private final String localFilesPath;
    private final ReplicationService replicationService;

    // snapshot van de bestanden die we de vorige keer zagen
    private Set<String> knownFiles = new HashSet<>();

    public FileWatcher(String localFilesPath, ReplicationService replicationService, String namingServerUrl) {
        this.localFilesPath = localFilesPath;
        this.replicationService = replicationService;
    }

    // start de watcher en slaat de huidige bestanden eerst op als beginsituatie
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

    // blijft lopen in de achtergrond en controleert regelmatig de map
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

    // vergelijkt de vorige snapshot met de huidige bestanden in de map
    private void checkChanges() {
        File folder = new File(localFilesPath);
        File[] currentArray = folder.listFiles();
        Set<String> currentFiles = new HashSet<>();
        if (currentArray != null) {
            for (File f : currentArray) {
                if (f.isFile()) currentFiles.add(f.getName());
            }
        }

        // als nieuw bestand -> replicate
        for (String name : currentFiles) {
            if (!knownFiles.contains(name)) {
                System.out.println("[FileWatcher] Nieuw bestand: " + name + " - repliceren...");
                replicationService.replicateFile(new File(folder, name));
            }
        }

        // als bestand verwijderd is -> replica bij owner verwijderen
        for (String name : knownFiles) {
            if (!currentFiles.contains(name)) {
                System.out.println("[FileWatcher] Bestand verwijderd: " + name + " - replica verwijderen...");
                replicationService.deleteReplica(name);
            }
        }

        knownFiles = currentFiles;
    }
}
