package discovery.ciscos.distlab4.service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Periodically POSTs a heartbeat to the naming server to keep the node in RUNNING state.
 * Simple, dependency-free HTTP client using HttpURLConnection for the lab environment.
 */
public class HeartbeatSender implements AutoCloseable {
    private final String baseUrl; // e.g., http://localhost:8080
    private final String nodeName;
    private final String ip; // optional but recommended for auto-registration on first heartbeat
    private final long periodMs;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "heartbeat-sender");
        t.setDaemon(true);
        return t;
    });

    public HeartbeatSender(String baseUrl, String nodeName, String ip) {
        this(baseUrl, nodeName, ip, 2000, 1000, 1000);
    }

    public HeartbeatSender(String baseUrl, String nodeName, String ip, long periodMs, int connectTimeoutMs, int readTimeoutMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length()-1) : baseUrl;
        this.nodeName = Objects.requireNonNull(nodeName, "nodeName");
        this.ip = ip; // may be null
        this.periodMs = periodMs;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public void start() {
        ses.scheduleAtFixedRate(this::sendOnce, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    private void sendOnce() {
        try {
            StringBuilder sb = new StringBuilder(baseUrl)
                    .append("/naming/membership/heartbeat?nodeName=")
                    .append(URLEncoder.encode(nodeName, StandardCharsets.UTF_8));
            if (ip != null && !ip.isBlank()) {
                sb.append("&ip=").append(URLEncoder.encode(ip, StandardCharsets.UTF_8));
            }
            URL url = new URL(sb.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            // Workaround for some servers expecting a body on POST: send empty body
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(new byte[0]);
            }
            int code = conn.getResponseCode();
            System.out.println("[Heartbeat] POST " + url + " -> " + code);
        } catch (Exception e) {
            System.out.println("[Heartbeat] send failed: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        ses.shutdownNow();
    }
}
