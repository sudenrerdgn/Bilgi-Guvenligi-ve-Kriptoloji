package tr.edu.crypto;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class RelayServer implements AutoCloseable {
    private final ServerSocket listener;
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final Map<String, Peer> peers = new LinkedHashMap<>();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final History history = new History();
    private final FileStore files;
    private final Map<String, PendingFile> pendingFiles = new ConcurrentHashMap<>();
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor();
    private record PendingFile(Peer sender, Peer recipient, FileStore.StoredFile file) {}
    public FileStore files() { return files; }

    private record Peer(String name, Socket socket, OutputStream output) {
        void send(String type, String sender, String text) throws IOException {
            Protocol.write(output, new Protocol.Frame(type, sender, text));
        }
    }

    public RelayServer(int port) throws IOException {
        this(port, Path.of("runtime", "server"));
    }

    public RelayServer(int port, Path directory) throws IOException {
        files = new FileStore(directory);
        listener = new ServerSocket(port);
        history.add("system", "Sunucu", "Sunucu bağlantıları bekliyor.");
        workers.submit(this::acceptConnections);
    }

    public int port() { return listener.getLocalPort(); }

    private void acceptConnections() {
        while (!listener.isClosed()) {
            try {
                Socket socket = listener.accept();
                sockets.add(socket);
                workers.submit(() -> handleConnection(socket));
            } catch (IOException exception) {
                if (!listener.isClosed()) history.add("error", "Sunucu", exception.getMessage());
            }
        }
    }

    private void handleConnection(Socket socket) {
        Peer peer = null;
        FileStore.Incoming incoming = null;
        try (socket) {
            socket.setSoTimeout(5000);
            Protocol.Frame hello = Protocol.read(socket.getInputStream());
            if (!hello.type().equals("HELLO")) throw new IOException("İlk mesaj HELLO olmalı.");
            String name = hello.sender().strip();
            Protocol.validateName(name);
            Peer candidate = new Peer(name, socket, socket.getOutputStream());
            synchronized (peers) {
                if (peers.keySet().stream().anyMatch(existing -> existing.equalsIgnoreCase(name))) {
                    candidate.send("ERROR", "Sunucu", "Bu kullanıcı adı zaten bağlı.");
                    return;
                }
                if (peers.size() >= 2) {
                    candidate.send("ERROR", "Sunucu", "Sunucu en fazla iki istemci kabul eder.");
                    return;
                }
                candidate.send("READY", "Sunucu", "Bağlantı kuruldu.");
                peers.put(name, candidate);
                peer = candidate;
            }
            socket.setSoTimeout(0);
            history.add("system", name, "İstemci bağlandı: " + socket.getInetAddress().getHostAddress());
            while (!socket.isClosed()) {
                Protocol.Frame frame = Protocol.read(socket.getInputStream());
                if (frame.type().equals("FILE_START")) {
                    if (incoming != null) throw new IOException("Önce mevcut PDF aktarımını tamamlayın.");
                    incoming = files.begin(frame, peer.name());
                    continue;
                }
                if (frame.type().equals("FILE_CHUNK")) {
                    if (incoming == null) throw new IOException("PDF başlangıcı bulunamadı.");
                    incoming.append(frame);
                    continue;
                }
                if (frame.type().equals("FILE_END")) {
                    if (incoming == null) throw new IOException("PDF başlangıcı bulunamadı.");
                    FileStore.StoredFile file = incoming.finish(frame.sender());
                    incoming.close();
                    incoming = null;
                    history.add("system", peer.name(), "PDF sunucuya kaydedildi: " + file.name());
                    forwardFile(peer, file);
                    continue;
                }
                if (frame.type().equals("FILE_ACK")) {
                    PendingFile pending = pendingFiles.get(frame.sender());
                    if (pending != null && pending.recipient() == peer && pendingFiles.remove(frame.sender(), pending)) {
                        history.add("system", peer.name(), "PDF teslimi onaylandı: " + pending.file().name());
                        try { pending.sender().send("FILE_ACK", frame.sender(), "PDF alıcıya kaydedildi."); }
                        catch (IOException ignored) { /* Alicinin baglantisini gonderici hatasi yuzunden kapatmayiz. */ }
                    }
                    continue;
                }
                if (!frame.type().equals("CHAT")) throw new IOException("Desteklenmeyen mesaj türü.");
                try {
                    Protocol.validateText(frame.text());
                    forwardMessage(peer, frame.text());
                } catch (IllegalArgumentException exception) {
                    peer.send("ERROR", "Sunucu", exception.getMessage());
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            if (!(exception instanceof EOFException) && !listener.isClosed()) {
                history.add("error", peer == null ? "Bağlantı" : peer.name(),
                        Objects.toString(exception.getMessage(), "Bağlantı hatası."));
            }
        } finally {
            if (incoming != null) {
                try { incoming.close(); } catch (IOException ignored) { /* Kapanista temizleme denenir. */ }
            }
            sockets.remove(socket);
            if (peer != null) {
                failPending(peer);
                synchronized (peers) { peers.remove(peer.name(), peer); }
                history.add("system", peer.name(), "İstemci ayrıldı.");
            }
        }
    }

    private void forwardFile(Peer sender, FileStore.StoredFile file) throws IOException {
        Peer recipient;
        synchronized (peers) {
            recipient = peers.values().stream().filter(peer -> peer != sender).findFirst().orElse(null);
        }
        if (recipient == null) {
            sender.send("FILE_ERROR", file.id(), "PDF sunucuda kayıtlı; diğer istemci bağlı olmadığı için iletilemedi.");
            return;
        }
        PendingFile pending = new PendingFile(sender, recipient, file);
        pendingFiles.put(file.id(), pending);
        deadlines.schedule(() -> {
            if (pendingFiles.remove(file.id(), pending)) notifyFileError(pending, "PDF teslim onayı zaman aşımına uğradı.");
        }, 30, TimeUnit.SECONDS);
        try (InputStream input = Files.newInputStream(file.path())) {
            FileStore.transmit(recipient.output(), file.id(), file.name(), sender.name(), file.size(), input, ignored -> {});
        } catch (IOException exception) {
            pendingFiles.remove(file.id(), pending);
            recipient.socket().close();
            notifyFileError(pending, "PDF sunucuda kayıtlı; alıcıya iletilemedi.");
        }
    }

    private void notifyFileError(PendingFile pending, String message) {
        history.add("error", pending.sender().name(), message);
        try { pending.sender().send("FILE_ERROR", pending.file().id(), message); }
        catch (IOException ignored) { /* Gonderici de ayrilmis olabilir. */ }
    }

    private void failPending(Peer peer) {
        pendingFiles.forEach((id, pending) -> {
            if ((pending.sender() == peer || pending.recipient() == peer) && pendingFiles.remove(id, pending)) {
                notifyFileError(pending, "Bağlantı kesildi; PDF teslimi doğrulanamadı.");
            }
        });
    }

    private void forwardMessage(Peer sender, String text) throws IOException {
        Peer recipient;
        synchronized (peers) {
            recipient = peers.values().stream().filter(peer -> peer != sender).findFirst().orElse(null);
        }
        if (recipient == null) {
            sender.send("ERROR", "Sunucu", "Diğer istemci bağlı değil; mesaj iletilemedi.");
            return;
        }
        try {
            // Kullanici adini mesajdan degil, sunucudaki baglanti kaydindan aliriz.
            recipient.send("MESSAGE", sender.name(), text);
        } catch (IOException exception) {
            recipient.socket().close();
            sender.send("ERROR", "Sunucu", "Alıcı bağlantısı kesildi; mesaj iletilemedi.");
            return;
        }
        history.add("message", sender.name(), text);
        // Bu onay TCP'ye yazildigini belirtir; kullanicinin okudugu anlamina gelmez.
        sender.send("SENT", sender.name(), text);
    }

    public Map<String, Object> state() {
        synchronized (peers) {
            return Map.of("role", "server", "connected", true, "tcpPort", port(),
                    "clients", List.copyOf(peers.keySet()), "history", history.snapshot(),
                    "files", files.snapshot(), "storage", files.directory());
        }
    }

    @Override public void close() throws IOException {
        listener.close();
        for (Socket socket : sockets) {
            try { socket.close(); } catch (IOException ignored) { /* Diger soketleri de kapat. */ }
        }
        workers.shutdownNow();
        deadlines.shutdownNow();
    }
}
