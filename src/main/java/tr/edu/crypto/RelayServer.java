package tr.edu.crypto;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public final class RelayServer implements AutoCloseable {
    private final ServerSocket listener;
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final Map<String, Peer> peers = new LinkedHashMap<>();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final History history = new History();

    private record Peer(String name, Socket socket, OutputStream output) {
        void send(String type, String sender, String text) throws IOException {
            Protocol.write(output, new Protocol.Frame(type, sender, text));
        }
    }

    public RelayServer(int port) throws IOException {
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
            sockets.remove(socket);
            if (peer != null) {
                synchronized (peers) { peers.remove(peer.name(), peer); }
                history.add("system", peer.name(), "İstemci ayrıldı.");
            }
        }
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
                    "clients", List.copyOf(peers.keySet()), "history", history.snapshot());
        }
    }

    @Override public void close() throws IOException {
        listener.close();
        for (Socket socket : sockets) {
            try { socket.close(); } catch (IOException ignored) { /* Diger soketleri de kapat. */ }
        }
        workers.shutdownNow();
    }
}
