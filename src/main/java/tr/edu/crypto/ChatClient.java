package tr.edu.crypto;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public final class ChatClient implements AutoCloseable {
    private final History history = new History();
    private Socket socket;
    private OutputStream output;
    private SecureChannel channel;
    private String mode = "insecure", method = "";
    private Map<String, Object> lastMeasurement = Map.of();
    private final Measurements measurements = new Measurements();
    private volatile Map<String, Object> benchmark = Map.of("running", false, "status", "Henüz ölçüm yapılmadı.");
    private boolean measuring, cancelMeasurement;
    private Socket measurementSocket;
    private String name = "";
    private String endpoint = "";
    private final FileStore files;
    private final ReentrantLock uploadLock = new ReentrantLock();
    private final Map<String, CompletableFuture<Void>> acknowledgements = new ConcurrentHashMap<>();
    private volatile Map<String, Object> transfer = Map.of();

    public ChatClient() { this(Path.of("runtime", "client-" + UUID.randomUUID())); }
    public ChatClient(Path directory) { files = new FileStore(directory); }
    public FileStore files() { return files; }

    public synchronized void connect(String host, int port, String username) throws IOException {
        connect(host, port, username, "insecure", "RSA", "");
    }

    public synchronized void connect(String host, int port, String username, String selectedMode,
                                     String selectedMethod, String fingerprint) throws IOException {
        if (socket != null) throw new IllegalArgumentException("Önce mevcut bağlantıyı kesin.");
        if (measuring) throw new IllegalArgumentException("Önce ölçümün bitmesini bekleyin.");
        if (!Set.of("secure", "insecure").contains(selectedMode)) throw new IllegalArgumentException("Geçersiz bağlantı modu.");
        if (selectedMode.equals("secure")) { Crypto.validateMethod(selectedMethod); Crypto.normalizeFingerprint(fingerprint); }
        String cleanName = username.strip();
        Protocol.validateName(cleanName);
        if (host.isBlank()) throw new IllegalArgumentException("Sunucu adresini girin.");
        Socket connection = new Socket();
        try {
            connection.connect(new InetSocketAddress(host.strip(), port), 3000);
            connection.setSoTimeout(15000);
            connection.setTcpNoDelay(true);
            OutputStream stream = connection.getOutputStream();
            SecureChannel transport = new SecureChannel(connection.getInputStream(), stream);
            Map<String, Object> sample = Map.of();
            if (selectedMode.equals("secure")) sample = KeyExchange.client(transport, cleanName, selectedMethod, fingerprint, false).snapshot();
            else transport.write(new Protocol.Frame("HELLO", cleanName, ""));
            Protocol.Frame reply = transport.read();
            if (!reply.type().equals("READY")) throw new IOException(reply.text());
            connection.setSoTimeout(0);
            socket = connection;
            output = stream;
            channel = transport; mode = selectedMode; method = selectedMode.equals("secure") ? selectedMethod : "";
            lastMeasurement = sample;
            name = cleanName;
            endpoint = host.strip() + ":" + port;
            transfer = Map.of();
            history.add("system", "Bağlantı", "Sunucuya bağlanıldı: " + endpoint
                    + (transport.secure() ? " · AES-128-GCM / " + method : " · Güvensiz mod"));
            Thread reader = new Thread(() -> receiveMessages(connection, transport), "client-reader");
            reader.setDaemon(true);
            reader.start();
        } catch (IOException | RuntimeException exception) {
            connection.close();
            throw exception;
        }
    }

    private void receiveMessages(Socket connection, SecureChannel transport) {
        FileStore.Incoming incoming = null;
        try {
            while (!connection.isClosed()) {
                Protocol.Frame frame = transport.read();
                if (transport.secure() && frame.type().startsWith("FILE_")) throw new IOException("Güvenli PDF desteklenmiyor.");
                switch (frame.type()) {
                    case "FILE_START" -> {
                        if (incoming != null) throw new IOException("Üst üste PDF aktarımı.");
                        incoming = files.begin(frame, new String(frame.data(), StandardCharsets.UTF_8));
                    }
                    case "FILE_CHUNK" -> {
                        if (incoming == null) throw new IOException("PDF başlangıcı bulunamadı.");
                        incoming.append(frame);
                    }
                    case "FILE_END" -> {
                        if (incoming == null) throw new IOException("PDF başlangıcı bulunamadı.");
                        FileStore.StoredFile file = incoming.finish(frame.sender());
                        incoming.close();
                        incoming = null;
                        history.add("system", file.sender(), "PDF alındı: " + file.name());
                        transport.write(new Protocol.Frame("FILE_ACK", file.id(), ""));
                    }
                    case "FILE_ACK", "FILE_ERROR" -> {
                        CompletableFuture<Void> future = acknowledgements.get(frame.sender());
                        if (future != null) {
                            if (frame.type().equals("FILE_ACK")) future.complete(null);
                            else future.completeExceptionally(new IOException(frame.text()));
                        }
                    }
                    case "MESSAGE", "SENT" -> history.add("message", frame.sender(), frame.text());
                    case "ERROR" -> history.add("error", "Sunucu", frame.text());
                    default -> throw new IOException("Beklenmeyen sunucu mesajı.");
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            synchronized (this) {
                if (socket == connection) history.add("error", "Bağlantı", "Sunucu bağlantısı kesildi. Modu ve bağlantıyı kontrol edip yeniden bağlanın.");
            }
        } finally {
            if (incoming != null) {
                try { incoming.close(); } catch (IOException ignored) { /* Eksik dosyayi temizlemeyi dene. */ }
            }
            synchronized (this) {
                if (socket == connection) {
                    socket = null; output = null; channel = null;
                    failUploads();
                }
            }
            try { connection.close(); } catch (IOException ignored) { /* Soket zaten kapanmis olabilir. */ }
        }
    }

    public void sendFile(String filename, byte[] bytes) throws IOException {
        FileStore.validate(filename, bytes.length);
        FileStore.validateHeader(bytes);
        if (!uploadLock.tryLock()) throw new IllegalArgumentException("Bir PDF aktarımı zaten sürüyor.");
        String id = UUID.randomUUID().toString();
        CompletableFuture<Void> completed = new CompletableFuture<>();
        try {
            OutputStream destination;
            String username;
            synchronized (this) {
                if (socket == null) throw new IOException("Önce sunucuya bağlanın.");
                if (channel.secure()) throw new IOException("Güvenli modda PDF aktarımı sonraki aşamada eklenecek.");
                destination = output;
                username = name;
                acknowledgements.put(id, completed);
            }
            transfer = Map.of("name", filename, "percent", 0, "status", "Sunucuya gönderiliyor");
            FileStore.transmit(destination, id, filename, username, bytes.length, new ByteArrayInputStream(bytes),
                    sent -> transfer = Map.of("name", filename, "percent", sent * 100 / bytes.length,
                            "status", "Sunucuya gönderiliyor"));
            transfer = Map.of("name", filename, "percent", 100, "status", "Alıcının kayıt onayı bekleniyor");
            completed.get(35, TimeUnit.SECONDS);
            transfer = Map.of("name", filename, "percent", 100, "status", "Alıcıya teslim edildi");
            history.add("system", username, "PDF alıcıya teslim edildi: " + filename);
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            String message = exception instanceof ExecutionException ? exception.getCause().getMessage()
                    : exception instanceof TimeoutException ? "Teslim onayı alınamadı; aktarım doğrulanamadı." : exception.getMessage();
            transfer = Map.of("name", filename, "percent", 0, "status", "Aktarım başarısız: " + message);
            history.add("error", "PDF", message);
            throw new IOException(message, exception);
        } finally {
            acknowledgements.remove(id);
            uploadLock.unlock();
        }
    }

    private void failUploads() {
        acknowledgements.values().forEach(future -> future.completeExceptionally(new IOException("Sunucu bağlantısı kesildi.")));
    }

    public synchronized void sendMessage(String text) throws IOException {
        Protocol.validateText(text);
        if (socket == null) throw new IllegalArgumentException("Önce sunucuya bağlanın.");
        channel.write(new Protocol.Frame("CHAT", name, text));
    }

    public synchronized Map<String, Object> state() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("role", "client"); state.put("connected", socket != null); state.put("name", name);
        state.put("endpoint", endpoint); state.put("history", history.snapshot()); state.put("files", files.snapshot());
        state.put("transfer", transfer); state.put("mode", mode); state.put("method", method);
        state.put("lastMeasurement", lastMeasurement); state.put("measurements", measurements.snapshot()); state.put("benchmark", benchmark);
        return state;
    }

    public String measurementsCsv() { return measurements.csv(); }

    public void benchmark(String host, int port, String selectedMethod, String fingerprint) throws IOException {
        Crypto.validateMethod(selectedMethod); Crypto.normalizeFingerprint(fingerprint);
        if (host.isBlank() || port < 1 || port > 65535) throw new IllegalArgumentException("Sunucu adresi ve portunu kontrol edin.");
        synchronized (this) {
            if (socket != null || measuring) throw new IllegalArgumentException("Ölçüm için bağlantıyı kesin ve devam eden ölçümü bekleyin.");
            measuring = true; cancelMeasurement = false;
            benchmark = Map.of("running", true, "status", selectedMethod + ": 0 / 10");
        }
        List<KeyExchange.Measurement> samples = new ArrayList<>();
        try {
            for (int index = 0; index < 10; index++) {
                try (Socket connection = new Socket()) {
                    synchronized (this) {
                        if (cancelMeasurement) throw new IOException("Ölçüm iptal edildi.");
                        measurementSocket = connection;
                    }
                    connection.connect(new InetSocketAddress(host.strip(), port), 3000);
                    connection.setSoTimeout(15000); connection.setTcpNoDelay(true);
                    SecureChannel transport = new SecureChannel(connection.getInputStream(), connection.getOutputStream());
                    KeyExchange.Measurement sample = KeyExchange.client(transport, "Olcum", selectedMethod, fingerprint, true);
                    Protocol.Frame ready = transport.read();
                    if (!ready.type().equals("READY")) throw new IOException("Ölçüm onaylanmadı.");
                    samples.add(sample);
                    benchmark = Map.of("running", true, "status", selectedMethod + ": " + (index + 1) + " / 10");
                }
            }
            measurements.save(selectedMethod, host.strip() + ":" + port, samples);
            benchmark = Map.of("running", false, "status", selectedMethod + ": 10 ölçüm tamamlandı. Ortalama tabloya eklendi.");
        } catch (IOException | RuntimeException exception) {
            benchmark = Map.of("running", false, "status", "Ölçüm tamamlanamadı (" + samples.size()
                    + "/10): " + exception.getMessage() + " Önceki tamamlanmış sonuçlar korundu.");
            throw exception;
        } finally {
            synchronized (this) { measuring = false; measurementSocket = null; }
        }
    }

    @Override public synchronized void close() throws IOException {
        Socket previous = socket;
        socket = null;
        output = null;
        channel = null;
        cancelMeasurement = true;
        if (measurementSocket != null) measurementSocket.close();
        failUploads();
        if (previous != null) {
            previous.close();
            history.add("system", "Bağlantı", "Bağlantı kapatıldı.");
        }
    }
}
