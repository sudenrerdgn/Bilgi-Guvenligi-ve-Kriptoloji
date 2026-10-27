package tr.edu.crypto;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public final class SecureModeTest {
    private static int checks;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        testHkdf();
        testRecords();
        testServerProof();
        Path directory = Files.createTempDirectory("crypto-secure-test-");
        try (RelayServer server = new RelayServer(0, directory.resolve("server"));
             WebUi serverUi = new WebUi(0, server);
             ChatClient first = new ChatClient(directory.resolve("a"));
             ChatClient second = new ChatClient(directory.resolve("b"));
             WebUi firstUi = new WebUi(0, first::state, first);
             WireProxy proxy = new WireProxy(server.port())) {
            String pin = (String) server.state().get("fingerprint");
            check(pin.matches("[0-9a-f]{64}"), "Sunucu SHA-256 parmak izi gosteriliyor");
            first.connect("127.0.0.1", server.port(), "Zeynep");
            check(post(serverUi, "/api/mode", "mode=secure", "http://example.com").statusCode() == 403,
                    "Yabanci kaynaktan sunucu modu degistirilemiyor");
            check(post(firstUi, "/api/mode", "mode=secure", null).statusCode() == 404,
                    "Istemci HTTP API sunucu modunu degistiremiyor");
            check(post(serverUi, "/api/mode", "mode=invalid", null).statusCode() == 400,
                    "Gecersiz mod reddediliyor");
            check(post(serverUi, "/api/mode", "mode=secure", null).statusCode() == 200,
                    "Sunucu dugmesinin API'si guvenli moda geciyor");
            await(() -> !connected(first));
            check(server.state().get("clients").equals(List.of()), "Mod degisiminde mevcut istemciler ayriliyor");
            expectFailure(() -> first.connect("127.0.0.1", server.port(), "Zeynep"), "Guvenli sunucu acik metin baglantisini reddediyor");
            expectFailure(() -> first.connect("127.0.0.1", server.port(), "Zeynep", "secure", "RSA", "0".repeat(64)),
                    "Yanlis sunucu parmak izi reddediliyor");
            check(post(firstUi, "/api/connect", form(server.port(), "RSA", ""), null).statusCode() == 400,
                    "Parmak izi olmadan guvenli baglanti baslatilamiyor");

            try (Socket raw = new Socket("127.0.0.1", server.port())) {
                raw.setSoTimeout(5000);
                SecureChannel channel = new SecureChannel(raw.getInputStream(), raw.getOutputStream());
                KeyExchange.client(channel, "Dogrulama", "ECDH", pin, false);
                check(channel.read().type().equals("READY"), "Dogrudan protokol istemcisi guvenli baglanti kuruyor");
                channel.write(new Protocol.Frame("FILE_START", UUID.randomUUID().toString(), "10\ntest.pdf", Crypto.utf8("Dogrulama")));
                check(channel.read().type().equals("ERROR") && Files.notExists(directory.resolve("server")),
                        "Arayuz atlansa da sunucu guvenli PDF baslangicini reddediyor");
                Protocol.write(raw.getOutputStream(), new Protocol.Frame("CHAT", "Dogrulama", "ENJEKTE-ACIK-METIN"));
                expectFailure(channel::read, "Sunucu guvenli oturuma acik metin enjeksiyonunda soketi kapatiyor");
            }
            await(() -> server.state().get("clients").equals(List.of()));
            check(WebUi.json(server.state()).contains("Mesaj doğrulanamadı") && !WebUi.json(server.state()).contains("ENJEKTE-ACIK-METIN"),
                    "Dogrulama hatasi kaydediliyor, enjekte edilen metin mesaja donusmuyor");
            try (Socket raw = new Socket("127.0.0.1", server.port())) {
                raw.setSoTimeout(5000);
                Protocol.write(raw.getOutputStream(), new Protocol.Frame("SECURE_HELLO", "Yarim", "ECDH", Crypto.random(32)));
                check(Protocol.read(raw.getInputStream()).type().equals("KEY_SERVER"), "Yarim anahtar paylasimi baslatildi");
                server.setMode("insecure");
                expectFailure(() -> Protocol.read(raw.getInputStream()), "Mod degisimi devam eden anahtar paylasimini da kapatiyor");
                server.setMode("secure");
            }

            for (String method : Crypto.METHODS) {
                String nextMethod = Crypto.METHODS.get((Crypto.METHODS.indexOf(method) + 1) % Crypto.METHODS.size());
                check(post(firstUi, "/api/connect", form(proxy.port(), method, pin), null).statusCode() == 200,
                        method + ": HTTP uzerinden guvenli baglanti kuruluyor");
                second.connect("127.0.0.1", proxy.port(), "Ece", "secure", nextMethod, pin);
                String message = "GIZLI-" + method + "-" + UUID.randomUUID() + " Türkçe ığüşöç 💛\n\"metin\"";
                first.sendMessage(message);
                await(() -> stateText(second).contains(WebUi.json(message)) && stateText(first).contains(WebUi.json(message)));
                check(WebUi.json(server.state()).contains(WebUi.json(message)), method + ": sunucu cozer ve farkli yontemli aliciya iletir");
                ExecutorService senders = Executors.newFixedThreadPool(2);
                try {
                    Future<?> a = senders.submit(() -> sendMany(first, method + "-A-"));
                    Future<?> b = senders.submit(() -> sendMany(second, method + "-B-"));
                    a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
                    await(() -> stateText(second).contains(method + "-A-19") && stateText(first).contains(method + "-B-19"));
                } finally { senders.shutdownNow(); }
                check(true, method + ": iki yonde eszamanli sifreli mesajlar korunuyor");
                check(!new String(proxy.bytes(), StandardCharsets.UTF_8).contains(message), method + ": TCP kaydinda mesaj acik metin bulunmuyor");
                @SuppressWarnings("unchecked") Map<String, Object> sample = (Map<String, Object>) first.state().get("lastMeasurement");
                check(((Number) sample.get("exchangeMs")).doubleValue() > 0
                        && ((Number) sample.get("totalBytes")).longValue() > ((Number) sample.get("keyPayloadBytes")).longValue(),
                        method + ": gercek sure ve bayt olcumleri mevcut");
                try (ChatClient duplicate = new ChatClient()) {
                    expectFailure(() -> duplicate.connect("127.0.0.1", server.port(), "zeynep", "secure", method, pin),
                            method + ": guvenli modda tekrar eden isim reddediliyor");
                    expectFailure(() -> duplicate.connect("127.0.0.1", server.port(), "Ucuncu", "secure", method, pin),
                            method + ": guvenli modda ucuncu istemci reddediliyor");
                }
                expectFailure(() -> first.sendFile("test.pdf", "%PDF-1.4 sample".getBytes(StandardCharsets.UTF_8)),
                        method + ": guvenli modda PDF sunucuya gonderilmiyor");
                check(Files.notExists(directory.resolve("server")), method + ": guvenli PDF diske yazilmiyor");
                server.setMode("secure");
                check(connected(first) && connected(second), "Ayni moda tekrar gecis baglantilari kapatmiyor");
                first.close(); second.close();
                await(() -> server.state().get("clients").equals(List.of()));
                check(post(firstUi, "/api/benchmark", form(server.port(), method, pin), null).statusCode() == 200,
                        method + ": gercek TCP uzerinden on tekrar tamamlandi");
                check(server.state().get("clients").equals(List.of()), "Olcum baglantilari sohbet yuvalarini doldurmuyor");
            }
            String csv = first.measurementsCsv();
            check(csv.lines().count() == 41, "CSV dort yontemin 40 ham olcumunu iceriyor");
            @SuppressWarnings("unchecked") List<Map<String, Object>> rows = (List<Map<String, Object>>) first.state().get("measurements");
            check(rows.size() == 4 && rows.stream().allMatch(row -> row.get("count").equals(10)), "Tablo dort yontemin onar olcumunu gosteriyor");
            List<String> rsaRows = csv.lines().filter(line -> line.startsWith("RSA,")).toList();
            double mean = rsaRows.stream().mapToDouble(line -> Double.parseDouble(line.split(",")[6])).average().orElseThrow();
            check(Math.abs(mean - ((Number) rows.getFirst().get("exchangeMs")).doubleValue()) < 0.000001,
                    "Tablodaki ortalama ham CSV olcumleriyle ayni");
            check(get(firstUi, "/api/measurements.csv").body().equals(csv), "Olcum CSV dosyasi HTTP uzerinden indiriliyor");
            expectFailure(() -> first.benchmark("127.0.0.1", server.port(), "RSA", "0".repeat(64)),
                    "Basarisiz deney tabloya on tekrar olarak eklenmiyor");
            check(first.measurementsCsv().equals(csv), "Basarisiz deney onceki gercek sonuclari koruyor");
            first.connect("127.0.0.1", server.port(), "Zeynep", "secure", "RSA", pin);
            server.setMode("insecure");
            await(() -> !connected(first));
            expectFailure(() -> first.connect("127.0.0.1", server.port(), "Zeynep", "secure", "RSA", pin),
                    "Guvenli baglanti istegi otomatik acik metne dusurulmuyor");
            first.connect("127.0.0.1", server.port(), "Zeynep");
            second.connect("127.0.0.1", server.port(), "Ece");
            first.sendMessage("Moddan sonra guvensiz metin");
            await(() -> stateText(second).contains("Moddan sonra guvensiz metin"));
            check(true, "Guvenli moddan guvensiz moda geri donulebiliyor");
            first.sendFile("geri.pdf", "%PDF-1.4 insecure regression".getBytes(StandardCharsets.UTF_8));
            check(second.files().snapshot().size() == 1, "Geri donuste guvensiz PDF aktarimi calisiyor");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        HTTP.close();
        System.out.println("BASARILI: " + checks + " guvenli mod kontrolu tamamlandi.");
    }

    private static void testHkdf() throws Exception {
        // RFC 5869, test case 1; uygulamanin kendi uretecegi sonuc yerine bagimsiz vektor.
        HexFormat hex = HexFormat.of();
        byte[] actual = Crypto.hkdf(hex.parseHex("0b".repeat(22)), hex.parseHex("000102030405060708090a0b0c"),
                hex.parseHex("f0f1f2f3f4f5f6f7f8f9"), 42);
        check(hex.formatHex(actual).equals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
                "HKDF RFC 5869 bilinen test vektoruyle uyumlu");
        check(Crypto.DH_P.bitLength() == 2048 && Crypto.DH_P.isProbablePrime(100)
                && Crypto.DH_P.subtract(java.math.BigInteger.ONE).shiftRight(1).isProbablePrime(100), "DH parametresi 2048 bit guvenli asal");
    }

    private static void testRecords() throws Exception {
        byte[] secret = Crypto.random(32), transcript = Crypto.random(32);
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        SecureChannel sender = new SecureChannel(InputStream.nullInputStream(), wire);
        sender.activate(secret, transcript, true);
        Protocol.Frame message = new Protocol.Frame("CHAT", "Zeynep", "Gizli mesaj ığüşöç");
        sender.write(message);
        byte[] first = wire.toByteArray();
        sender.write(message);
        byte[] second = Arrays.copyOfRange(wire.toByteArray(), first.length, wire.size());
        check(!Arrays.equals(first, second), "Ayni metnin tekrarinda nonce ve sifreli kayit farkli");
        SecureChannel receiver = receiver(wire.toByteArray(), secret, transcript, false);
        check(receiver.read().text().equals(message.text()) && receiver.read().text().equals(message.text()), "GCM kayitlari sirayla cozuluyor");
        byte[] tampered = first.clone(); tampered[tampered.length - 1] ^= 1;
        expectFailure(() -> receiver(tampered, secret, transcript, false).read(), "Degistirilen GCM etiketi reddediliyor");
        byte[] replay = new byte[first.length * 2]; System.arraycopy(first, 0, replay, 0, first.length); System.arraycopy(first, 0, replay, first.length, first.length);
        SecureChannel replayReceiver = receiver(replay, secret, transcript, false); replayReceiver.read();
        expectFailure(replayReceiver::read, "Tekrar gonderilen kayit reddediliyor");
        expectFailure(() -> receiver(first, secret, transcript, true).read(), "Ters yonde anahtar kullanimi reddediliyor");
        expectFailure(() -> receiver(Crypto.encode(message), secret, transcript, false).read(), "Sifreli kanala acik metin enjekte edilemiyor");
        expectFailure(() -> receiver(Arrays.copyOf(first, first.length - 1), secret, transcript, false).read(), "Eksik sifreli kayit reddediliyor");
    }

    private static SecureChannel receiver(byte[] wire, byte[] secret, byte[] transcript, boolean client) throws Exception {
        SecureChannel receiver = new SecureChannel(new ByteArrayInputStream(wire), OutputStream.nullOutputStream());
        receiver.activate(secret, transcript, client); return receiver;
    }

    private static void testServerProof() throws Exception {
        // Guvenilen kimlik anahtari sunulsa bile imzasiz/gecersiz el sikisma kabul edilmez.
        KeyPair identity = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair rsa = Crypto.generate("RSA");
        Protocol.Frame fake = new Protocol.Frame("KEY_SERVER", "", "1", Crypto.pack(identity.getPublic().getEncoded(),
                rsa.getPublic().getEncoded(), Crypto.random(32), new byte[64]));
        SecureChannel fakeChannel = new SecureChannel(new ByteArrayInputStream(Crypto.encode(fake)), OutputStream.nullOutputStream());
        expectFailure(() -> KeyExchange.client(fakeChannel, "Zeynep", "RSA", Crypto.fingerprint(identity.getPublic()), false),
                "Dogru parmak izi ama gecersiz sunucu imzasi reddediliyor");
    }

    private static void sendMany(ChatClient client, String prefix) {
        try { for (int index = 0; index < 20; index++) client.sendMessage(prefix + index); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    private static boolean connected(ChatClient client) { return Boolean.TRUE.equals(client.state().get("connected")); }
    private static String stateText(ChatClient client) { return WebUi.json(client.state()); }
    private static String form(int port, String method, String pin) {
        return "host=127.0.0.1&port=" + port + "&name=Zeynep&mode=secure&method=" + method + "&fingerprint=" + pin;
    }
    private static HttpResponse<String> post(WebUi ui, String path, String body, String origin) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + ui.port() + path))
                .timeout(Duration.ofSeconds(90)).header("Content-Type", "application/x-www-form-urlencoded");
        if (origin != null) builder.header("Origin", origin);
        return HTTP.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> get(WebUi ui, String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + ui.port() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > limit) throw new AssertionError("Beklenen durum olusmadi.");
            Thread.sleep(20);
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); checks++; System.out.println("OK: " + message);
    }
    private static void expectFailure(Action action, String message) throws Exception {
        try { action.run(); } catch (IOException exception) { check(true, message); return; }
        throw new AssertionError(message);
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }

    // Gercek TCP trafigini iki yonde kopyalar; yalnizca testte kullanilir, pcapng yerine gecmez.
    private static final class WireProxy implements AutoCloseable {
        private final ServerSocket listener = new ServerSocket(0);
        private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        WireProxy(int target) throws IOException {
            workers.submit(() -> {
                while (!listener.isClosed()) {
                    try {
                        Socket source = listener.accept(), destination = new Socket("127.0.0.1", target);
                        source.setTcpNoDelay(true); destination.setTcpNoDelay(true);
                        sockets.add(source); sockets.add(destination);
                        workers.submit(() -> pipe(source, destination)); workers.submit(() -> pipe(destination, source));
                    } catch (IOException exception) { if (!listener.isClosed()) throw new UncheckedIOException(exception); }
                }
            });
        }
        int port() { return listener.getLocalPort(); }
        byte[] bytes() { synchronized (captured) { return captured.toByteArray(); } }
        private void pipe(Socket source, Socket destination) {
            try {
                byte[] buffer = new byte[8192]; int size;
                while ((size = source.getInputStream().read(buffer)) != -1) {
                    synchronized (captured) { captured.write(buffer, 0, size); }
                    destination.getOutputStream().write(buffer, 0, size); destination.getOutputStream().flush();
                }
            } catch (IOException ignored) { /* Testte iki soket birlikte kapanir. */ }
            finally {
                try { source.close(); destination.close(); } catch (IOException ignored) { }
                sockets.remove(source); sockets.remove(destination);
            }
        }
        @Override public void close() throws IOException {
            listener.close(); for (Socket socket : sockets) socket.close(); workers.shutdownNow(); workers.close();
        }
    }
}
