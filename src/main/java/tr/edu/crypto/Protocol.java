package tr.edu.crypto;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class Protocol {
    private static final int MAX_FRAME_BYTES = 65_536;

    private Protocol() {}

    public record Frame(String type, String sender, String text, byte[] data) {
        public Frame(String type, String sender, String text) {
            this(type, sender, text, new byte[0]);
        }
    }

    // TCP mesaj sinirlarini korumaz; her cercevenin basina uzunlugunu ekleriz.
    public static void write(OutputStream output, Frame frame) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream payload = new DataOutputStream(buffer);
        for (String value : new String[]{frame.type(), frame.sender(), frame.text()}) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            payload.writeInt(bytes.length);
            payload.write(bytes);
        }
        // PDF baytlarini metne veya Base64'e cevirmeden oldugu gibi tasiriz.
        payload.write(frame.data());
        if (buffer.size() > MAX_FRAME_BYTES) throw new IOException("Mesaj boyutu sınırı aşıldı.");
        // Ayni sokete yazan iki is parcaciginin verileri birbirine karismamalidir.
        synchronized (output) {
            DataOutputStream stream = new DataOutputStream(output);
            stream.writeInt(buffer.size());
            buffer.writeTo(stream);
            stream.flush();
        }
    }

    public static Frame read(InputStream input) throws IOException {
        DataInputStream stream = new DataInputStream(input);
        int size = stream.readInt();
        if (size < 12 || size > MAX_FRAME_BYTES) throw new IOException("Geçersiz çerçeve boyutu.");
        byte[] bytes = stream.readNBytes(size);
        if (bytes.length != size) throw new EOFException("Eksik çerçeve.");
        DataInputStream payload = new DataInputStream(new ByteArrayInputStream(bytes));
        String[] fields = new String[3];
        for (int index = 0; index < fields.length; index++) {
            int length = payload.readInt();
            if (length < 0 || length > payload.available()) throw new IOException("Geçersiz alan boyutu.");
            fields[index] = new String(payload.readNBytes(length), StandardCharsets.UTF_8);
        }
        return new Frame(fields[0], fields[1], fields[2], payload.readAllBytes());
    }

    public static void validateName(String name) {
        if (name.isBlank() || name.length() > 40 || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Kullanıcı adı 1–40 karakter olmalı ve kontrol karakteri içermemeli.");
        }
    }

    public static void validateText(String text) {
        if (text.isBlank() || text.length() > 4000) {
            throw new IllegalArgumentException("Mesaj 1–4000 karakter olmalı.");
        }
    }
}
