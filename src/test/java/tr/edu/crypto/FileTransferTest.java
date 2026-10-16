package tr.edu.crypto;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public final class FileTransferTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("crypto-file-test-");
        try (RelayServer server = new RelayServer(0, root.resolve("server"));
             ChatClient first = new ChatClient(root.resolve("first"));
             ChatClient second = new ChatClient(root.resolve("second"));
             WebUi firstUi = new WebUi(0, first::state, first);
             WebUi secondUi = new WebUi(0, second::state, second);
             WebUi serverUi = new WebUi(0, server::state, null, server.files())) {
            byte[] pdf = new byte[180_000];
            new Random(17).nextBytes(pdf);
            System.arraycopy("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII), 0, pdf, 0, 9);
            first.connect("127.0.0.1", server.port(), "Sude");
            check(upload(firstUi, "örnek.pdf", pdf).statusCode() == 503, "Alici yoksa basari bildirilmiyor");
            second.connect("127.0.0.1", server.port(), "Ece");
            check(upload(firstUi, "örnek.pdf", pdf).statusCode() == 200, "Parcali PDF aktarimi alici ACK ile tamamlaniyor");
            Map<String, Object> received = second.files().snapshot().getFirst();
            String id = received.get("id").toString();
            byte[] downloaded = download(secondUi, id).body();
            check(Arrays.equals(pdf, downloaded), "Indirilen PDF kaynakla bayt bayt ayni");
            check(Arrays.equals(MessageDigest.getInstance("SHA-256").digest(pdf),
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(server.files().find(id).path()))),
                    "Sunucudaki kopyanin SHA-256 ozeti kaynakla ayni");
            check(Arrays.equals(download(serverUi, id).body(), pdf), "Sunucu ekranindan PDF indirilebiliyor");
            check(download(secondUi, "missing").statusCode() == 404, "Bilinmeyen dosya indirilemiyor");
            check(download(secondUi, id).headers().firstValue("Content-Disposition").orElse("").contains("filename*=UTF-8''"),
                    "Turkce indirme adi korunuyor");
            check(upload(firstUi, "../escape.pdf", pdf).statusCode() == 400, "Dosya adinda yol gecisi reddediliyor");
            check(upload(firstUi, "fake.pdf", "not a PDF".getBytes()).statusCode() == 400, "PDF basligi olmayan dosya reddediliyor");
            check(upload(firstUi, "file.txt", pdf).statusCode() == 400, "PDF disi uzanti reddediliyor");
            check(upload(firstUi, "empty.pdf", new byte[0]).statusCode() == 400, "Bos dosya reddediliyor");
            check(upload(firstUi, "big.pdf", new byte[FileStore.MAX_BYTES + 1]).statusCode() == 400,
                    "10 MB sinirini asan dosya reddediliyor");
            ExecutorService senders = Executors.newFixedThreadPool(2);
            try {
                Future<?> a = senders.submit(() -> send(first, pdf));
                Future<?> b = senders.submit(() -> send(second, pdf));
                a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
                check(first.files().snapshot().size() == 1 && second.files().snapshot().size() == 2,
                        "Iki yonde eszamanli PDF aktarimi tamamlanabiliyor");
            } finally { senders.shutdownNow(); }
            check(second.files().snapshot().stream().map(file -> file.get("id")).distinct().count() == 2,
                    "Ayni adli dosyalar birbirinin uzerine yazilmiyor");
            first.sendMessage("PDF-sonrasi-mesaj");
            await(() -> WebUi.json(second.state()).contains("PDF-sonrasi-mesaj"));
            check(true, "PDF sonrasi metin iletisimi devam ediyor");
            byte[] sample = Files.readAllBytes(Path.of("samples", "wireshark-test.pdf"));
            check(upload(firstUi, "wireshark-test.pdf", sample).statusCode() == 200,
                    "Repodaki gecerli ornek PDF de aktarilabiliyor");
            String sampleId = second.files().snapshot().stream().filter(file -> file.get("name").equals("wireshark-test.pdf"))
                    .findFirst().orElseThrow().get("id").toString();
            check(Arrays.equals(download(secondUi, sampleId).body(), sample), "Ornek PDF indirildiginde degismiyor");
            second.close();
            await(() -> !WebUi.json(server.state().get("clients")).contains("Ece"));
            String partialId = UUID.randomUUID().toString();
            try (Socket raw = new Socket("127.0.0.1", server.port())) {
                Protocol.write(raw.getOutputStream(), new Protocol.Frame("HELLO", "Eksik", ""));
                Protocol.read(raw.getInputStream());
                Protocol.write(raw.getOutputStream(), new Protocol.Frame("FILE_START", partialId, "100\neksik.pdf"));
                Protocol.write(raw.getOutputStream(), new Protocol.Frame("FILE_CHUNK", partialId, "", "%PDF-".getBytes()));
                await(() -> Files.exists(root.resolve("server").resolve(partialId + ".part")));
            }
            await(() -> !Files.exists(root.resolve("server").resolve(partialId + ".part")));
            check(server.files().find(partialId) == null, "Kesilen aktarimin eksik dosyasi temizleniyor");
        } finally {
            // Yalnizca bu testin olusturdugu gecici klasoru temizleriz.
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        System.out.println("BASARILI: " + checks + " PDF kontrolu tamamlandi.");
    }

    private static void send(ChatClient client, byte[] pdf) {
        try { client.sendFile("örnek.pdf", pdf); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    private static HttpResponse<String> upload(WebUi ui, String name, byte[] bytes) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + ui.port() + "/api/file?name="
                        + URLEncoder.encode(name, StandardCharsets.UTF_8))).timeout(Duration.ofSeconds(40))
                .header("Content-Type", "application/pdf").POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<byte[]> download(WebUi ui, String id) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + ui.port() + "/api/files/" + id))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Beklenen durum olusmadi.");
            Thread.sleep(20);
        }
    }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
        System.out.println("OK: " + label);
    }
}
