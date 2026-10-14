package tr.edu.crypto;

import java.io.*;
import java.net.*;
import java.util.Map;

public final class ChatClient implements AutoCloseable {
    private final History history = new History();
    private Socket socket;
    private OutputStream output;
    private String name = "";
    private String endpoint = "";

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
        try {
            while (!connection.isClosed()) {
                Protocol.Frame frame = Protocol.read(connection.getInputStream());
                switch (frame.type()) {
                    case "MESSAGE", "SENT" -> history.add("message", frame.sender(), frame.text());
                    case "ERROR" -> history.add("error", "Sunucu", frame.text());
                    default -> throw new IOException("Beklenmeyen sunucu mesajı.");
                }
            }
        } catch (IOException exception) {
            synchronized (this) {
                if (socket == connection) history.add("error", "Bağlantı", "Sunucu bağlantısı kesildi.");
            }
        } finally {
            synchronized (this) {
                if (socket == connection) { socket = null; output = null; }
            }
            try { connection.close(); } catch (IOException ignored) { /* Soket zaten kapanmis olabilir. */ }
        }
    }

    public synchronized void sendMessage(String text) throws IOException {
        Protocol.validateText(text);
        if (socket == null) throw new IllegalArgumentException("Önce sunucuya bağlanın.");
        Protocol.write(output, new Protocol.Frame("CHAT", name, text));
    }

    public synchronized Map<String, Object> state() {
        return Map.of("role", "client", "connected", socket != null, "name", name,
                "endpoint", endpoint, "history", history.snapshot());
    }

    @Override public synchronized void close() throws IOException {
        Socket previous = socket;
        socket = null;
        output = null;
        if (previous != null) {
            previous.close();
            history.add("system", "Bağlantı", "Bağlantı kapatıldı.");
        }
    }
}
