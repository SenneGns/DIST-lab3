package discovery.ciscos.distlab4;

import Replication.ciscos.distlab4.FileLog;
import agents.ciscos.distlab6.FailureAgent;
import agents.ciscos.distlab6.SyncAgent;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import discovery.ciscos.distlab4.service.NodeContext;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class NodeHttpServer {

    private final int port;
    private final NodeContext context;
    private final String replicaFilesPath;
    private final String namingServerUrl;
    private final FileLog fileLog;

    private SyncAgent syncAgent;
    private final String localFilesPath;

    public NodeHttpServer(int port, NodeContext context, String replicaFilesPath,
                          String namingServerUrl, FileLog fileLog, String localFilesPath) {
        this.port = port;
        this.context = context;
        this.replicaFilesPath = replicaFilesPath;
        this.namingServerUrl = namingServerUrl;
        this.fileLog = fileLog;
        this.localFilesPath = localFilesPath;
    }

    public void setSyncAgent(SyncAgent syncAgent) {
        this.syncAgent = syncAgent;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/node/ping", exchange ->
                sendResponse(exchange, 200, "OK")
        );

        server.createContext("/node/setNext", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            String val = params.getOrDefault("nextID", params.get("value"));
            if (val != null) {
                context.setNextID(Integer.parseInt(val));
                System.out.println("[NodeServer] nextID bijgewerkt naar " + val);
            }
            sendResponse(exchange, 200, "OK");
        });

        server.createContext("/node/setPrevious", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            String val = params.getOrDefault("previousID", params.get("value"));
            if (val != null) {
                context.setPreviousID(Integer.parseInt(val));
                System.out.println("[NodeServer] previousID bijgewerkt naar " + val);
            }
            sendResponse(exchange, 200, "OK");
        });

        server.createContext("/node/localFileTerminating", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            String filename = params.get("filename");
            String sourceIp = params.get("sourceIp");
            if (filename != null && sourceIp != null) {
                handleLocalFileTerminating(filename, sourceIp);
            }
            sendResponse(exchange, 200, "OK");
        });

        server.createContext("/node/deleteReplica", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            String filename = params.get("filename");
            if (filename != null) {
                new File(replicaFilesPath, filename).delete();
                new FileLog(replicaFilesPath).removeEntry(filename);
                System.out.println("[NodeServer] Replica verwijderd: " + filename);
            }
            sendResponse(exchange, 200, "OK");
        });

        // Controleert of een bepaald bestand aanwezig is als replica op deze node.
        server.createContext("/node/hasFile", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            String filename = params.get("filename");
            if (filename == null) {
                sendResponse(exchange, 400, "Missing filename");
                return;
            }
            File f = new File(replicaFilesPath, filename);
            sendResponse(exchange, f.exists() ? 200 : 404, f.exists() ? "YES" : "NO");
        });

        // Geeft de agentlijst van de lokale SyncAgent terug als JSON.
        server.createContext("/agent/syncList", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }
            if (syncAgent == null) {
                sendResponse(exchange, 200, "{}");
                return;
            }
            Map<String, Boolean> list = syncAgent.getAgentFileList();
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Boolean> entry : list.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
                first = false;
            }
            json.append("}");
            sendResponse(exchange, 200, json.toString());
        });

        // Ontvangt een geserialiseerde agent, voert hem uit en stuurt hem door naar de volgende node.
        server.createContext("/node/receiveAgent", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }
            try {
                byte[] body = exchange.getRequestBody().readAllBytes();
                Object obj;
                try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(body))) {
                    obj = ois.readObject();
                }

                if (obj instanceof FailureAgent agent) {
                    // Terminatieconditie: agent is terug bij de startnode
                    if (agent.isDone(context.getCurrentID())) {
                        System.out.println("[NodeServer] FailureAgent klaar (terug bij startnode).");
                        sendResponse(exchange, 200, "DONE");
                        return;
                    }

                    // Context injecteren en uitvoeren
                    agent.setContext(fileLog, replicaFilesPath, context.getCurrentID());
                    Thread t = new Thread(agent, "failure-agent");
                    t.start();
                    t.join();

                    // Doorsturen naar volgende node
                    String nextIp = getIpFromNamingServer(context.getNextID());
                    if (nextIp != null) {
                        agent.forwardToNode(nextIp);
                    } else {
                        System.out.println("[NodeServer] Geen IP gevonden voor volgende node.");
                    }
                } else {
                    System.err.println("[NodeServer] Onbekend agenttype ontvangen: "
                            + (obj == null ? "null" : obj.getClass().getName()));
                }
                sendResponse(exchange, 200, "OK");
            } catch (Exception e) {
                System.err.println("[NodeServer] Fout bij verwerken agent: " + e.getMessage());
                sendResponse(exchange, 500, "Error: " + e.getMessage());
            }
        });

        // Geeft de configuratie van deze node terug als JSON (voor de GUI).
        server.createContext("/node/info", exchange -> {
            String json = "{\"nodeName\":\"" + context.getNodeName() + "\",\"ip\":\"" + context.getIp()
                    + "\",\"currentID\":" + context.getCurrentID()
                    + ",\"previousID\":" + context.getPreviousID()
                    + ",\"nextID\":" + context.getNextID() + "}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            sendResponse(exchange, 200, json);
        });

        // Geeft de lokale bestanden van deze node terug als JSON (voor de GUI).
        server.createContext("/node/files/local", exchange -> {
            File folder = new File(localFilesPath);
            File[] files = folder.listFiles(File::isFile);
            StringBuilder json = new StringBuilder("[");
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    if (i > 0) json.append(",");
                    json.append("\"").append(files[i].getName()).append("\"");
                }
            }
            json.append("]");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            sendResponse(exchange, 200, json.toString());
        });

        // Geeft de gerepliceerde bestanden van deze node terug als JSON (voor de GUI).
        server.createContext("/node/files/replicated", exchange -> {
            java.util.List<FileLog.LogEntry> entries = fileLog.getEntriesCopy();
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) json.append(",");
                FileLog.LogEntry e = entries.get(i);
                json.append("{\"fileName\":\"").append(e.fileName)
                        .append("\",\"fileHash\":").append(e.fileHash)
                        .append(",\"downloadLocation\":\"").append(e.downloadLocation)
                        .append("\",\"ownerId\":").append(e.ownerId)
                        .append(",\"locked\":").append(e.locked).append("}");
            }
            json.append("]");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            sendResponse(exchange, 200, json.toString());
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("[NodeServer] HTTP server actief op poort " + port);
    }

    private void handleLocalFileTerminating(String filename, String sourceIp) {
        for (FileLog.LogEntry entry : fileLog.getEntries()) {
            if (!entry.fileName.equals(filename)) continue;

            if (sourceIp.equals(entry.downloadLocation)) {
                new File(replicaFilesPath, filename).delete();
                fileLog.removeEntry(filename);
                System.out.println("[NodeServer] Replica verwijderd: " + filename);
            } else {
                fileLog.updateDownloadLocation(filename, "");
                System.out.println("[NodeServer] Download location bijgewerkt voor: " + filename);
            }
            return;
        }
        System.out.println("[NodeServer] Geen log entry gevonden voor: " + filename);
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
            System.out.println("[NodeServer] Fout bij ophalen IP voor node " + nodeId + ": " + e.getMessage());
            return null;
        }
    }

    private void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> parseQuery(String query) {
        if (query == null) return Map.of();
        return Arrays.stream(query.split("&"))
                .map(p -> p.split("=", 2))
                .filter(p -> p.length == 2)
                .collect(Collectors.toMap(p -> p[0], p -> p[1]));
    }
}
