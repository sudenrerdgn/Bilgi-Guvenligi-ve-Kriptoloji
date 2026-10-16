package tr.edu.crypto;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class WebUi implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private final Supplier<Map<String, Object>> state;
    private final ChatClient client;
    private final FileStore files;

    public WebUi(int port, Supplier<Map<String, Object>> state, ChatClient client) throws IOException {
        this(port, state, client, client == null ? null : client.files());
    }

    public WebUi(int port, Supplier<Map<String, Object>> state, ChatClient client, FileStore files) throws IOException {
        this.state = state;
        this.client = client;
        this.files = files;
        // Tarayici arayuzu yalnizca uygulamanin calistigi bilgisayardan acilir.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(workers);
        server.start();
    }

    public int port() { return server.getAddress().getPort(); }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (!Set.of("127.0.0.1:" + port(), "localhost:" + port()).contains(Objects.toString(host, ""))) {
                respond(exchange, 403, "application/json", json(Map.of("error", "Geçersiz adres.")));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (method.equals("GET") && path.equals("/api/state")) {
                respond(exchange, 200, "application/json", json(state.get()));
            } else if (method.equals("GET") && path.startsWith("/api/files/") && files != null) {
                FileStore.StoredFile file = files.find(path.substring("/api/files/".length()));
                if (file == null) {
                    respond(exchange, 404, "application/json", "{\"error\":\"Dosya bulunamadı.\"}");
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/pdf");
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=download.pdf; filename*=UTF-8''"
                        + URLEncoder.encode(file.name(), StandardCharsets.UTF_8).replace("+", "%20"));
                exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, file.size());
                Files.copy(file.path(), exchange.getResponseBody());
            } else if (method.equals("GET") && Set.of("/", "/app.js", "/style.css").contains(path)) {
                String resource = path.equals("/") ? "index.html" : path.substring(1);
                try (InputStream input = getClass().getResourceAsStream("/web/" + resource)) {
                    if (input == null) throw new IOException("Arayüz dosyası bulunamadı.");
                    String mime = path.endsWith(".js") ? "text/javascript" : path.endsWith(".css") ? "text/css" : "text/html";
                    respond(exchange, 200, mime, new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            } else if (method.equals("POST") && client != null &&
                    Set.of("/api/connect", "/api/send", "/api/disconnect", "/api/file").contains(path)) {
                String origin = exchange.getRequestHeaders().getFirst("Origin");
                if (origin != null && !origin.equals("http://" + host)) {
                    respond(exchange, 403, "application/json", json(Map.of("error", "İstek kaynağı reddedildi.")));
                    return;
                }
                if (path.equals("/api/file")) {
                    String filename = parseForm(Objects.toString(exchange.getRequestURI().getRawQuery(), ""))
                            .getOrDefault("name", "");
                    byte[] pdf = exchange.getRequestBody().readNBytes(FileStore.MAX_BYTES + 1);
                    client.sendFile(filename, pdf);
                    respond(exchange, 200, "application/json", "{\"ok\":true}");
                    return;
                }
                byte[] bytes = exchange.getRequestBody().readNBytes(65_537);
                if (bytes.length > 65_536) throw new IllegalArgumentException("İstek çok büyük.");
                Map<String, String> form = parseForm(new String(bytes, StandardCharsets.UTF_8));
                switch (path) {
                    case "/api/connect" -> client.connect(form.getOrDefault("host", ""),
                            Integer.parseInt(form.getOrDefault("port", "5000")), form.getOrDefault("name", ""));
                    case "/api/send" -> client.sendMessage(form.getOrDefault("text", ""));
                    case "/api/disconnect" -> client.close();
                    default -> throw new IllegalArgumentException("Geçersiz işlem.");
                }
                respond(exchange, 200, "application/json", "{\"ok\":true}");
            } else {
                respond(exchange, 404, "application/json", "{\"error\":\"Adres bulunamadı.\"}");
            }
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, "application/json", json(Map.of("error", Objects.toString(exception.getMessage(), "Geçersiz girdi."))));
        } catch (IOException exception) {
            respond(exchange, 503, "application/json", json(Map.of("error", Objects.toString(exception.getMessage(), "Bağlantı hatası."))));
        } finally {
            exchange.close();
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> values = new HashMap<>();
        if (!body.isEmpty()) {
            for (String field : body.split("&")) {
                String[] parts = field.split("=", 2);
                values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    private static void respond(HttpExchange exchange, int status, String mime, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", mime + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    // JSON metinlerinde tirnaklari, ters egik cizgileri ve kontrol karakterlerini kaciririz.
    public static String json(Object value) {
        if (value instanceof Map<?, ?> map) return map.entrySet().stream()
                .map(entry -> json(entry.getKey().toString()) + ":" + json(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
        if (value instanceof Collection<?> list) return list.stream().map(WebUi::json).collect(Collectors.joining(",", "[", "]"));
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        StringBuilder result = new StringBuilder("\"");
        for (char character : value.toString().toCharArray()) {
            if (character == '"' || character == '\\') result.append('\\').append(character);
            else if (character < 32) result.append(String.format("\\u%04x", (int) character));
            else result.append(character);
        }
        return result.append('"').toString();
    }

    @Override public void close() {
        server.stop(0);
        workers.shutdownNow();
    }
}
