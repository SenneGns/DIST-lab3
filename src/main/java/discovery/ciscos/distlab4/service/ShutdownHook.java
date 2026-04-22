package discovery.ciscos.distlab4.service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Best-effort graceful leave on JVM shutdown. Posts to the naming server's leave endpoint
 * with short retries and a small overall time budget.
 */
public class ShutdownHook {
    private final String baseUrl; // e.g., http://localhost:8080
    private final String nodeName; // identify by name; server hashes it

    private int maxAttempts = 4;
    private int connectTimeoutMs = 1500;
    private int readTimeoutMs = 1500;
    private long totalBudgetMs = 8000;

    public ShutdownHook(String baseUrl, String nodeName) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.nodeName = nodeName;
    }

    public ShutdownHook withMaxAttempts(int attempts) { this.maxAttempts = attempts; return this; }
    public ShutdownHook withTimeouts(int connectMs, int readMs) { this.connectTimeoutMs = connectMs; this.readTimeoutMs = readMs; return this; }
    public ShutdownHook withBudgetMs(long ms) { this.totalBudgetMs = ms; return this; }

    public void register() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::leaveBestEffort, "discovery-leave-hook"));
    }

    private void leaveBestEffort() {
        long deadline = System.currentTimeMillis() + totalBudgetMs;
        int attempts = 0;
        while (attempts < maxAttempts && System.currentTimeMillis() < deadline) {
            attempts++;
            try {
                String urlStr = baseUrl + "/naming/nodes/leave?nodeName=" +
                        URLEncoder.encode(nodeName, StandardCharsets.UTF_8);
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(connectTimeoutMs);
                conn.setReadTimeout(readTimeoutMs);
                int code = conn.getResponseCode();
                System.out.println("[ShutdownHook] leave attempt=" + attempts + ", response=" + code);
                if (code >= 200 && code < 300) return; // success
            } catch (Exception e) {
                System.out.println("[ShutdownHook] leave attempt " + attempts + " failed: " + e.getMessage());
            }
            // jittered backoff 200-600ms
            try { Thread.sleep(200 + (long)(Math.random() * 400)); } catch (InterruptedException ignored) {}
        }
        System.out.println("[ShutdownHook] leave did not confirm within budget; exiting anyway.");
    }
}
