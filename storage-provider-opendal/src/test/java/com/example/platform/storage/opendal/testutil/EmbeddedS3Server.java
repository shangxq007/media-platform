package com.example.platform.storage.opendal.testutil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;

/**
 * Minimal in-process S3-compatible HTTP server for integration testing.
 * Handles HTTP/1.1 with proper connection management.
 */
public class EmbeddedS3Server {

    private final int requestedPort;
    private HttpServer server;
    private final ConcurrentMap<String, ConcurrentMap<String, byte[]>> buckets = new ConcurrentHashMap<>();
    private volatile boolean running;
    private int actualPort;

    public EmbeddedS3Server(int port) {
        this.requestedPort = port;
    }

    public void start() throws Exception {
        if (running) return;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", requestedPort), 100);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(16, r -> {
            Thread t = new Thread(r, "s3w");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        actualPort = server.getAddress().getPort();
        running = true;
        // Verify server is listening
        for (int i = 0; i < 100; i++) {
            try (Socket s = new Socket("127.0.0.1", actualPort)) {
                if (s.isConnected()) return;
            } catch (IOException e) {
                Thread.sleep(50);
            }
        }
        throw new IOException("Server not reachable on port " + actualPort);
    }

    public void stop() {
        running = false;
        if (server != null) server.stop(0);
        buckets.clear();
    }

    public boolean isRunning() { return running && server != null; }
    public String getEndpoint() { return "http://127.0.0.1:" + actualPort; }
    public int getPort() { return actualPort; }

    private void handle(HttpExchange ex) {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            String[] p = path.split("/", 3);
            if (p.length < 2) { sendErr(ex, 400, "Bad"); return; }
            String bucket = dec(p[1]);
            String key = p.length > 2 ? dec(p[2]) : null;

            if ("PUT".equals(method)) {
                if (key == null || key.isEmpty()) {
                    buckets.computeIfAbsent(bucket, k -> new ConcurrentHashMap<>());
                    sendResp(ex, 200, null, null, null);
                } else {
                    String cs = ex.getRequestHeaders().getFirst("x-amz-copy-source");
                    if (cs != null && !cs.isEmpty()) { handleCopy(ex, bucket, key, cs); }
                    else {
                        buckets.computeIfAbsent(bucket, k -> new ConcurrentHashMap<>());
                        byte[] data = read(ex.getRequestBody());
                        buckets.get(bucket).put(key, data);
                        Map<String, String> rh = new HashMap<>();
                        rh.put("ETag", "\"" + md5(data) + "\"");
                        sendResp(ex, 200, null, null, rh);
                    }
                }
            } else if ("GET".equals(method)) {
                if (key == null || key.isEmpty()) { handleList(ex, bucket); }
                else { handleGet(ex, bucket, key, ex.getRequestHeaders().getFirst("Range")); }
            } else if ("HEAD".equals(method)) {
                ConcurrentMap<String, byte[]> bd = buckets.get(bucket);
                if (bd == null) { sendErr(ex, 404, "No bucket"); return; }
                byte[] data = bd.get(key);
                if (data == null) { sendErr(ex, 404, "No key"); return; }
                Map<String, String> rh = new HashMap<>();
                rh.put("Content-Length", String.valueOf(data.length));
                rh.put("Content-Type", "application/octet-stream");
                rh.put("ETag", "\"" + md5(data) + "\"");
                sendResp(ex, 200, null, null, rh);
            } else if ("DELETE".equals(method)) {
                ConcurrentMap<String, byte[]> bd = buckets.get(bucket);
                if (bd != null) bd.remove(key);
                sendResp(ex, 204, null, null, null);
            } else { sendResp(ex, 405, null, null, null); }
        } catch (Exception e) {
            try { sendErr(ex, 500, "Err: " + e.getMessage()); } catch (IOException ignored) {}
        } finally { ex.close(); }
    }

    private void handleCopy(HttpExchange ex, String tb, String tk, String src) throws IOException {
        String[] sp = src.split("/", 3);
        ConcurrentMap<String, byte[]> s = buckets.get(dec(sp[1]));
        if (s == null) { sendErr(ex, 404, "No src bucket"); return; }
        byte[] data = s.get(dec(sp[2]));
        if (data == null) { sendErr(ex, 404, "No src key"); return; }
        buckets.computeIfAbsent(tb, k -> new ConcurrentHashMap<>());
        buckets.get(tb).put(tk, data);
        String xml = "<?xml version=\"1.0\"?><CopyObjectResult><LastModified>2024-01-01T00:00:00Z</LastModified><ETag>\"" + md5(data) + "\"</ETag></CopyObjectResult>";
        byte[] r = xml.getBytes();
        Map<String, String> rh = new HashMap<>();
        rh.put("Content-Type", "application/xml");
        sendResp(ex, 200, "application/xml", r, rh);
    }

    private void handleGet(HttpExchange ex, String b, String k, String range) throws IOException {
        ConcurrentMap<String, byte[]> bd = buckets.get(b);
        if (bd == null) { sendErr(ex, 404, "No bucket"); return; }
        byte[] data = bd.get(k);
        if (data == null) { sendErr(ex, 404, "No key"); return; }
        if (range != null && range.startsWith("bytes=")) {
            String[] rp = range.substring(6).split("-");
            int s = Integer.parseInt(rp[0]);
            int e = (rp.length > 1 && !rp[1].isEmpty()) ? Integer.parseInt(rp[1]) : data.length - 1;
            if (e >= data.length) e = data.length - 1;
            byte[] r = new byte[e - s + 1];
            System.arraycopy(data, s, r, 0, r.length);
            Map<String, String> rh = new HashMap<>();
            rh.put("Content-Type", "application/octet-stream");
            rh.put("Content-Range", "bytes " + s + "-" + e + "/" + data.length);
            rh.put("ETag", "\"" + md5(data) + "\"");
            sendResp(ex, 206, "application/octet-stream", r, rh);
        } else {
            Map<String, String> rh = new HashMap<>();
            rh.put("Content-Type", "application/octet-stream");
            rh.put("ETag", "\"" + md5(data) + "\"");
            sendResp(ex, 200, "application/octet-stream", data, rh);
        }
    }

    private void handleList(HttpExchange ex, String b) throws IOException {
        ConcurrentMap<String, byte[]> bd = buckets.getOrDefault(b, new ConcurrentHashMap<>());
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\"?><ListBucketResult><Name>" + b + "</Name><KeyCount>" + bd.size() + "</KeyCount>");
        for (Map.Entry<String, byte[]> e : bd.entrySet())
            x.append("<Contents><Key>").append(e.getKey()).append("</Key><Size>").append(e.getValue().length).append("</Size></Contents>");
        x.append("</ListBucketResult>");
        byte[] r = x.toString().getBytes();
        Map<String, String> rh = new HashMap<>();
        rh.put("Content-Type", "application/xml");
        sendResp(ex, 200, "application/xml", r, rh);
    }

    private void sendErr(HttpExchange ex, int code, String msg) throws IOException {
        byte[] m = msg.getBytes();
        sendResp(ex, code, "text/plain", m, null);
    }

    private void sendResp(HttpExchange ex, int code, String ct, byte[] body, Map<String, String> extra) throws IOException {
        if (ct != null) ex.getResponseHeaders().set("Content-Type", ct);
        if (extra != null) {
            for (Map.Entry<String, String> h : extra.entrySet()) {
                ex.getResponseHeaders().set(h.getKey(), h.getValue());
            }
        }
        int len = (body != null) ? body.length : -1;
        ex.sendResponseHeaders(code, len);
        if (body != null) {
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        }
    }

    private byte[] read(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] c = new byte[8192]; int n;
        while ((n = is.read(c)) > 0) buf.write(c, 0, n);
        return buf.toByteArray();
    }

    private static String dec(String s) { try { return URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; } }
    private static String md5(byte[] d) {
        try { MessageDigest md = MessageDigest.getInstance("MD5"); byte[] h = md.digest(d);
            StringBuilder sb = new StringBuilder(); for (byte b : h) sb.append(String.format("%02x", b)); return sb.toString();
        } catch (Exception e) { return "d41d8cd98f00b204e9800998ecf8427e"; }
    }
}
