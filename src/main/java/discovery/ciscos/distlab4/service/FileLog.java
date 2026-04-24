package discovery.ciscos.distlab4.service;

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

        public LogEntry() {}

        public LogEntry(String fileName, int fileHash, String downloadLocation) {
            this.fileName = fileName;
            this.fileHash = fileHash;
            this.downloadLocation = downloadLocation;
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
}