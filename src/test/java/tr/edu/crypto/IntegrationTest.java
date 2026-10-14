package tr.edu.crypto;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public final class IntegrationTest {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static int checks;

    public static void main(String[] args) throws Exception {
        testFraming();
        try (RelayServer server = new RelayServer(0);
             ChatClient first = new ChatClient(); ChatClient second = new ChatClient();
             WebUi serverUi = new WebUi(0, server::state, null);
             WebUi firstUi = new WebUi(0, first::state, first);
             WebUi secondUi = new WebUi(0, second::state, second)) {
            check(get(serverUi, "/").body().contains("Kriptoloji"), "Sunucu HTML sayfasi sunuluyor");
            check(get(firstUi, "/app.js").statusCode() == 200 && get(firstUi, "/style.css").statusCode() == 200,
                    "Tarayici dosyalari sunuluyor");
            check(get(firstUi, "/missing").statusCode() == 404, "Bilinmeyen adres reddediliyor");
            check(post(firstUi, "/api/send", "text=erken").statusCode() == 400, "Baglantisiz gonderim reddediliyor");
            check(post(firstUi, "/api/connect", connectionForm(server.port(), "Sude")).statusCode() == 200,
                    "Birinci Java istemcisi HTTP uzerinden baglaniyor");
            first.sendMessage("alici-yok");
            await(() -> stateText(first).contains("Diğer istemci bağlı değil"));
            check(!stateText(first).contains("alici-yok"), "Alicisiz mesaj gonderilmis gibi gosterilmiyor");
            try (ChatClient duplicate = new ChatClient()) {
                expectFailure(() -> duplicate.connect("127.0.0.1", server.port(), "sude"), "Tekrarlanan ad reddediliyor");
            }
            check(post(secondUi, "/api/connect", connectionForm(server.port(), "Ece")).statusCode() == 200,
                    "Ikinci Java istemcisi baglaniyor");
            try (ChatClient third = new ChatClient()) {
                expectFailure(() -> third.connect("127.0.0.1", server.port(), "Zeynep"), "Ucuncu istemci reddediliyor");
            }
            String message = "Türkçe: ığüşöç İĞÜŞÖÇ 💛\n\"alıntı\" \\ <script>alert(1)</script>";
            check(post(firstUi, "/api/send", "text=" + encode(message)).statusCode() == 200, "Turkce mesaj kabul ediliyor");
            await(() -> stateText(second).contains("Türkçe:"));
            await(() -> stateText(first).contains("Türkçe:"));
            check(WebUi.json(server.state()).contains("Türkçe:"), "Sunucu mesaj icerigini kaydediyor");
            check(stateText(second).contains(WebUi.json(message)), "Unicode, satir sonu ve tirnaklar korunuyor");
            ExecutorService senders = Executors.newFixedThreadPool(2);
            try {
                Future<?> forward = senders.submit(() -> sendMany(first, "A"));
                Future<?> backward = senders.submit(() -> sendMany(second, "B"));
                forward.get(5, TimeUnit.SECONDS);
                backward.get(5, TimeUnit.SECONDS);
                await(() -> stateText(first).contains("B-19") && stateText(second).contains("A-19"));
                check(true, "Iki yonde eszamanli 40 mesaj iletiliyor");
            } finally { senders.shutdownNow(); }
            check(post(firstUi, "/api/send", "text=" + encode(" ")).statusCode() == 400, "Bos mesaj reddediliyor");
            check(post(firstUi, "/api/send", "text=" + "a".repeat(4001)).statusCode() == 400, "Uzun mesaj reddediliyor");
            HttpRequest foreign = HttpRequest.newBuilder(uri(firstUi, "/api/disconnect"))
                    .header("Origin", "http://example.com").POST(HttpRequest.BodyPublishers.noBody()).build();
            check(HTTP.send(foreign, HttpResponse.BodyHandlers.ofString()).statusCode() == 403,
                    "Yabanci web kaynagindan istek reddediliyor");
            check(post(secondUi, "/api/disconnect", "").statusCode() == 200, "Baglanti kapatilabiliyor");
            await(() -> !WebUi.json(server.state().get("clients")).contains("Ece"));
            check(post(secondUi, "/api/connect", connectionForm(server.port(), "Zeynep")).statusCode() == 200,
                    "Ayrilan istemcinin yerine yeniden baglanilabiliyor");
            server.close();
            await(() -> Boolean.FALSE.equals(first.state().get("connected")) && Boolean.FALSE.equals(second.state().get("connected")));
            check(true, "Sunucu kapaninca istemci durumlari guncelleniyor");
        }
        System.out.println("BASARILI: " + checks + " kontrol tamamlandi.");
    }

    private static void testFraming() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Protocol.Frame frame = new Protocol.Frame("CHAT", "Sude", "çok satırlı\nmesaj");
        Protocol.write(buffer, frame);
        Protocol.write(buffer, frame);
        // Agin veriyi birer baytlik parcalar halinde getirmesini taklit ederiz.
        InputStream fragmented = new FilterInputStream(new ByteArrayInputStream(buffer.toByteArray())) {
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                return super.read(bytes, offset, Math.min(length, 1));
            }
        };
        check(Protocol.read(fragmented).equals(frame) && Protocol.read(fragmented).equals(frame),
                "Bolunmus ve ardisik TCP cerceveleri okunuyor");
        expectFailure(() -> Protocol.read(new ByteArrayInputStream(new byte[]{127, 127, 127, 127})),
                "Asiri buyuk cerceve reddediliyor");
    }

    private static void sendMany(ChatClient client, String prefix) {
        try { for (int index = 0; index < 20; index++) client.sendMessage(prefix + "-" + index); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    private static String stateText(ChatClient client) { return WebUi.json(client.state()); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String connectionForm(int port, String name) { return "host=127.0.0.1&port=" + port + "&name=" + encode(name); }
    private static URI uri(WebUi ui, String path) { return URI.create("http://127.0.0.1:" + ui.port() + path); }
    private static HttpResponse<String> get(WebUi ui, String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(uri(ui, path)).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private static HttpResponse<String> post(WebUi ui, String path, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(uri(ui, path)).timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Beklenen durum olusmadi.");
            Thread.sleep(20);
        }
    }
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
        System.out.println("OK: " + description);
    }
    private static void expectFailure(CheckedAction action, String description) throws Exception {
        try { action.run(); }
        catch (IOException exception) { check(true, description); return; }
        throw new AssertionError(description);
    }
    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
}
