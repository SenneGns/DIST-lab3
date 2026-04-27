package discovery.ciscos.distlab4;

import Replication.ciscos.distlab4.FileLog;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import discovery.ciscos.distlab4.service.NodeContext;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class NodeHttpServer {

    private final int port;
    private final NodeContext context;
    private final String replicaFilesPath;

    public NodeHttpServer(int port, NodeContext context, String replicaFilesPath) {
        this.port = port;
        this.context = context;
        this.replicaFilesPath = replicaFilesPath;
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

        // Shutdown 3/3: owner ontvangt melding dat de node met de lokale kopie stopt
        server.createContext("/node/localFileTerminating", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            String filename = params.get("filename");
            String sourceIp = params.get("sourceIp");
            if (filename != null && sourceIp != null) {
                handleLocalFileTerminating(filename, sourceIp);
            }
            sendResponse(exchange, 200, "OK");
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("[NodeServer] HTTP server actief op poort " + port);
    }

    /**
     * Shutdown 3/3: de owner beslist of de replica verwijderd of bijgewerkt wordt.
     * Als downloadLocation gelijk is aan sourceIp, was dit de enige lokale kopie
     * → replica en log entry worden verwijderd.
     * Anders → alleen de download location wordt leeggemaakt in de log.
     */
    private void handleLocalFileTerminating(String filename, String sourceIp) {
        FileLog log = new FileLog(replicaFilesPath);
        for (FileLog.LogEntry entry : log.getEntries()) {
            if (!entry.fileName.equals(filename)) continue;

            if (sourceIp.equals(entry.downloadLocation)) {
                // Bestand nooit door andere nodes gedownload → verwijder replica
                new File(replicaFilesPath, filename).delete();
                log.removeEntry(filename);
                System.out.println("[NodeServer] Replica verwijderd: " + filename
                        + " (lokale kopie op " + sourceIp + " verdwijnt, nooit gedownload)");
            } else {
                // Andere nodes hebben het bestand → update download location
                log.updateDownloadLocation(filename, "");
                System.out.println("[NodeServer] Download location bijgewerkt voor: " + filename);
            }
            return;
        }
        System.out.println("[NodeServer] Geen log entry gevonden voor: " + filename);
    }

    private void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes();
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
