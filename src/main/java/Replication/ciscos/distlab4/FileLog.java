package Replication.ciscos.distlab4;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileLog {

    public static class LogEntry {
        public String fileName;
        public int fileHash;
        public String downloadLocation;

        // Lab 6 Agents uitbreiding
        public int ownerId;
        public boolean locked = false;

        public boolean downloadedByOthers = false;

        public LogEntry() {
        }

        public LogEntry(String fileName, int fileHash, String downloadLocation) {
            this.fileName = fileName;
            this.fileHash = fileHash;
            this.downloadLocation = downloadLocation;
            this.ownerId = -1;
            this.locked = false;
        }

        public LogEntry(String fileName, int fileHash, String downloadLocation, int ownerId) {
            this.fileName = fileName;
            this.fileHash = fileHash;
            this.downloadLocation = downloadLocation;
            this.ownerId = ownerId;
            this.locked = false;
        }

        @Override
        public String toString() {
            return "LogEntry{" +
                    "fileName='" + fileName + '\'' +
                    ", fileHash=" + fileHash +
                    ", downloadLocation='" + downloadLocation + '\'' +
                    ", ownerId=" + ownerId +
                    ", locked=" + locked +
                    ", downloadedByOthers=" + downloadedByOthers +
                    '}';
        }
    }

    private final File logFile;
    private final ObjectMapper mapper = new ObjectMapper();
    private List<LogEntry> entries;

    public FileLog(String logDirectory) {
        this.logFile = new File(logDirectory, "filelog.json");
        load();
    }

    public void addEntry(String fileName, int fileHash, String downloadLocation) {
        entries.add(new LogEntry(fileName, fileHash, downloadLocation));
        save();
        System.out.println("[FileLog] Entry toegevoegd: " + fileName + " van " + downloadLocation);
    }

    public void removeEntry(String fileName) {
        entries.removeIf(e -> e.fileName.equals(fileName));
        save();
    }

    public void updateDownloadLocation(String fileName, String newLocation) {
        entries.stream()
                .filter(e -> e.fileName.equals(fileName))
                .findFirst()
                .ifPresent(e -> { e.downloadLocation = newLocation; save(); });
    }

//    public void markDownloadedByOthers(String fileName) {
//        entries.stream()
//                .filter(e -> e.fileName.equals(fileName))
//                .findFirst()
//                .ifPresent(e -> { e.downloadedByOthers = true; save(); });
//    }

    public List<LogEntry> getEntries() {
        return entries;
    }


    private void save() {
        try {
            mapper.writeValue(logFile, entries);
        } catch (Exception e) {
            System.err.println("[FileLog] Fout bij opslaan: " + e.getMessage());
        }
    }

    private void load() {
        try {
            if (logFile.exists()) {
                entries = mapper.readValue(logFile, new TypeReference<>() {});
            } else {
                entries = new ArrayList<>();
            }
        } catch (Exception e) {
            entries = new ArrayList<>();
        }
    }
    public synchronized void addEntry(String fileName, int fileHash, String downloadLocation, int ownerId) {
        for (LogEntry entry : entries) {
            if (entry.fileName.equals(fileName)) {
                entry.fileHash = fileHash;
                entry.downloadLocation = downloadLocation;
                entry.ownerId = ownerId;
                save();
                return;
            }
        }

        entries.add(new LogEntry(fileName, fileHash, downloadLocation, ownerId));
        save();
    }

    public synchronized void setLocked(String fileName, boolean locked) {
        for (LogEntry entry : entries) {
            if (entry.fileName.equals(fileName)) {
                entry.locked = locked;
                save();
                return;
            }
        }
    }

    public synchronized boolean isLocked(String fileName) {
        for (LogEntry entry : entries) {
            if (entry.fileName.equals(fileName)) {
                return entry.locked;
            }
        }

        return false;
    }

    public synchronized void updateOwner(String fileName, int newOwnerId, String newDownloadLocation) {
        for (LogEntry entry : entries) {
            if (entry.fileName.equals(fileName)) {
                entry.ownerId = newOwnerId;
                entry.downloadLocation = newDownloadLocation;
                save();
                return;
            }
        }
    }

    public synchronized List<LogEntry> getEntriesCopy() {
        return new ArrayList<>(entries);
    }
}