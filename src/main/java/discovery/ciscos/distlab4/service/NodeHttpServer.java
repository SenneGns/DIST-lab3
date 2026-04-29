package discovery.ciscos.distlab4.service;

import Replication.ciscos.distlab4.FileLog;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class NodeHttpServer {

    private final int port;
    private final NodeContext context;
    private final String replicaFilesPath;

    public NodeHttpServer(int port, NodeContext context, String replicaFilesPath) {
        this.port = port;
        this.context = context;
        this.replicaFilesPath = replicaFilesPath;
    }

    public void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/node/ping", this::handlePing);
            server.createContext("/node/setNext", this::handleSetNext);
            server.createContext("/node/setPrevious", this::handleSetPrevious);
            server.createContext("/node/deleteReplica", this::handleDeleteReplica);
            server.createContext("/node/localFileTerminating", this::handleLocalFileTerminating);
            server.start();
            System.out.println("[NodeHttp] Server actief op poort " + port);
        } catch (IOException e) {
            System.err.println("[NodeHttp] Fout bij starten: " + e.getMessage());
        }
    }

    private void handlePing(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed");
            return;
        }
        send(exchange, 200, "OK");
    }

    private void handleSetNext(HttpExchange exchange) throws IOException {
        Integer value = getIntQueryValue(exchange, "nextID", "value");
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()) || value == null) {
            send(exchange, 400, "Bad request");
            return;
        }
        context.setNextID(value);
        send(exchange, 200, "OK");
    }

    private void handleSetPrevious(HttpExchange exchange) throws IOException {
        Integer value = getIntQueryValue(exchange, "previousID", "value");
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()) || value == null) {
            send(exchange, 400, "Bad request");
            return;
        }
        context.setPreviousID(value);
        send(exchange, 200, "OK");
    }

    private void handleDeleteReplica(HttpExchange exchange) throws IOException {
        if (!"DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed");
            return;
        }
        String fileName = parseQuery(exchange).get("filename");
        if (fileName == null || fileName.isBlank()) {
            send(exchange, 400, "Missing filename");
            return;
        }

        File replica = new File(replicaFilesPath, fileName);
        if (replica.exists() && !replica.delete()) {
            send(exchange, 500, "Could not delete replica");
            return;
        }
        new FileLog(replicaFilesPath).removeEntry(fileName);
        send(exchange, 200, "OK");
    }

    private void handleLocalFileTerminating(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed");
            return;
        }
        send(exchange, 200, "OK");
    }

    private Integer getIntQueryValue(HttpExchange exchange, String... keys) {
        Map<String, String> query = parseQuery(exchange);
        for (String key : keys) {
            String value = query.get(key);
            if (value == null) continue;
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Map<String, String> parseQuery(HttpExchange exchange) {
        Map<String, String> values = new HashMap<>();
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) return values;

        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
