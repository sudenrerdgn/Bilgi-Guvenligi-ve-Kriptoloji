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
        if (socket != null) throw new IllegalArgumentException("Önce mevcut bağlantıyı kesin.");
        String cleanName = username.strip();
        Protocol.validateName(cleanName);
        if (host.isBlank()) throw new IllegalArgumentException("Sunucu adresini girin.");
        Socket connection = new Socket();
        try {
            connection.connect(new InetSocketAddress(host.strip(), port), 3000);
            connection.setSoTimeout(5000);
            OutputStream stream = connection.getOutputStream();
            Protocol.write(stream, new Protocol.Frame("HELLO", cleanName, ""));
            Protocol.Frame reply = Protocol.read(connection.getInputStream());
            if (!reply.type().equals("READY")) throw new IOException(reply.text());
            connection.setSoTimeout(0);
            socket = connection;
            output = stream;
            name = cleanName;
            endpoint = host.strip() + ":" + port;
            history.add("system", "Bağlantı", "Sunucuya bağlanıldı: " + endpoint);
            Thread reader = new Thread(() -> receiveMessages(connection), "client-reader");
            reader.setDaemon(true);
            reader.start();
        } catch (IOException | RuntimeException exception) {
            connection.close();
            throw exception;
        }
    }

    private void receiveMessages(Socket connection) {
        FileStore.Incoming incoming = null;
        try {
            while (!connection.isClosed()) {
                Protocol.Frame frame = Protocol.read(connection.getInputStream());
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
                        Protocol.write(connection.getOutputStream(), new Protocol.Frame("FILE_ACK", file.id(), ""));
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
                if (socket == connection) history.add("error", "Bağlantı", "Sunucu bağlantısı kesildi.");
            }
        } finally {
            if (incoming != null) {
                try { incoming.close(); } catch (IOException ignored) { /* Eksik dosyayi temizlemeyi dene. */ }
            }
            synchronized (this) {
                if (socket == connection) {
                    socket = null; output = null;
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
        Protocol.write(output, new Protocol.Frame("CHAT", name, text));
    }

    public synchronized Map<String, Object> state() {
        return Map.of("role", "client", "connected", socket != null, "name", name,
                "endpoint", endpoint, "history", history.snapshot(), "files", files.snapshot(), "transfer", transfer);
    }

    @Override public synchronized void close() throws IOException {
        Socket previous = socket;
        socket = null;
        output = null;
        failUploads();
        if (previous != null) {
            previous.close();
            history.add("system", "Bağlantı", "Bağlantı kapatıldı.");
        }
    }
}
