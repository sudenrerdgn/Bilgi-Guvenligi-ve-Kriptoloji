package tr.edu.crypto;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

public final class FileStore {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int CHUNK_BYTES = 32 * 1024;
    private final Path directory;
    private final Map<String, StoredFile> files = new ConcurrentHashMap<>();

    public record StoredFile(String id, String name, String sender, long size, Path path, String time) {
        public Map<String, Object> metadata() {
            return Map.of("id", id, "name", name, "sender", sender, "size", size, "time", time);
        }
    }

    public FileStore(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    public String directory() { return directory.toString(); }
    public StoredFile find(String id) { return files.get(id); }
    public List<Map<String, Object>> snapshot() {
        return files.values().stream().sorted(Comparator.comparing(StoredFile::time))
                .map(StoredFile::metadata).toList();
    }

    public static void validate(String name, long size) {
        if (name.isBlank() || name.length() > 180 || !name.toLowerCase(Locale.ROOT).endsWith(".pdf")
                || name.matches(".*[\\\\/:*?\"<>|].*") || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Geçerli bir PDF dosya adı seçin.");
        }
        if (size < 5 || size > MAX_BYTES) throw new IllegalArgumentException("PDF boyutu 5 bayt ile 10 MB arasında olmalı.");
    }

    public static void validateHeader(byte[] bytes) {
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw new IllegalArgumentException("Dosyanın PDF başlığı geçersiz.");
        }
    }

    public Incoming begin(Protocol.Frame frame, String sender) throws IOException {
        String id = UUID.fromString(frame.sender()).toString();
        int separator = frame.text().indexOf('\n');
        if (separator < 1) throw new IOException("Geçersiz PDF başlangıç bilgisi.");
        long size = Long.parseLong(frame.text().substring(0, separator));
        String name = frame.text().substring(separator + 1);
        validate(name, size);
        Files.createDirectories(directory);
        // Diskte kullanici dosya adi yerine benzersiz kimlik kullaniriz.
        return new Incoming(id, name, sender, size);
    }

    public final class Incoming implements AutoCloseable {
        private final String id, name, sender;
        private final long size;
        private final Path partial;
        private final OutputStream output;
        private long received;

        private Incoming(String id, String name, String sender, long size) throws IOException {
            this.id = id; this.name = name; this.sender = sender; this.size = size;
            partial = directory.resolve(id + ".part");
            if (Files.exists(directory.resolve(id + ".pdf"))) throw new IOException("Dosya kimliği zaten kullanılıyor.");
            output = Files.newOutputStream(partial, StandardOpenOption.CREATE_NEW);
        }
        public String id() { return id; }
        public void append(Protocol.Frame frame) throws IOException {
            if (!frame.sender().equals(id) || frame.data().length == 0 || frame.data().length > CHUNK_BYTES
                    || received + frame.data().length > size) throw new IOException("Geçersiz PDF parçası.");
            output.write(frame.data());
            received += frame.data().length;
        }
        public StoredFile finish(String transferId) throws IOException {
            if (!transferId.equals(id) || received != size) throw new IOException("PDF aktarımı eksik.");
            output.close();
            try (InputStream input = Files.newInputStream(partial)) { validateHeader(input.readNBytes(5)); }
            Path target = directory.resolve(id + ".pdf");
            Files.move(partial, target);
            StoredFile file = new StoredFile(id, name, sender, size, target, Instant.now().toString());
            files.put(id, file);
            return file;
        }
        @Override public void close() throws IOException {
            try { output.close(); } finally { Files.deleteIfExists(partial); }
        }
    }

    public static void transmit(OutputStream output, String id, String name, String sender,
                                long size, InputStream input, LongConsumer progress) throws IOException {
        Protocol.write(output, new Protocol.Frame("FILE_START", id, size + "\n" + name,
                sender.getBytes(StandardCharsets.UTF_8)));
        long sent = 0;
        byte[] chunk;
        while ((chunk = input.readNBytes(CHUNK_BYTES)).length > 0) {
            Protocol.write(output, new Protocol.Frame("FILE_CHUNK", id, "", chunk));
            sent += chunk.length;
            progress.accept(sent);
        }
        Protocol.write(output, new Protocol.Frame("FILE_END", id, ""));
    }
}
