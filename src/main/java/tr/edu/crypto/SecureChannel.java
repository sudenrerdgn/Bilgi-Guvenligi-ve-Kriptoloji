package tr.edu.crypto;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

// Tek okuyucu; yazicilar siralama ve GCM nonce sayaci icin ayni kilidi kullanir.
public final class SecureChannel {
    private final InputStream input;
    private final OutputStream output;
    private SecretKey sendKey, receiveKey;
    private byte[] sendPrefix, receivePrefix;
    private long sendSequence, receiveSequence;
    private long sentBytes, receivedBytes;
    private volatile boolean failed;

    public SecureChannel(InputStream input, OutputStream output) { this.input = input; this.output = output; }
    public boolean secure() { return sendKey != null; }
    public long sentBytes() { return sentBytes; }
    public long receivedBytes() { return receivedBytes; }

    void activate(byte[] shared, byte[] transcriptHash, boolean client) throws GeneralSecurityException {
        if (secure()) throw new IllegalStateException("Kanal zaten güvenli.");
        byte[] material = Crypto.hkdf(shared, transcriptHash, Crypto.utf8("kriptoloji-v1 traffic keys"), 40);
        try {
            SecretKey c2s = new SecretKeySpec(material, 0, 16, "AES");
            SecretKey s2c = new SecretKeySpec(material, 16, 16, "AES");
            byte[] cNonce = Arrays.copyOfRange(material, 32, 36), sNonce = Arrays.copyOfRange(material, 36, 40);
            sendKey = client ? c2s : s2c; receiveKey = client ? s2c : c2s;
            sendPrefix = client ? cNonce : sNonce; receivePrefix = client ? sNonce : cNonce;
        } finally { Arrays.fill(material, (byte) 0); }
    }

    public synchronized void write(Protocol.Frame frame) throws IOException {
        if (failed) throw new IOException("Kanal kullanılamıyor; yeniden bağlanın.");
        try {
            Protocol.Frame outgoing = frame;
            if (secure()) {
                if (sendSequence == Long.MAX_VALUE) throw new IOException("Oturum sınırına ulaşıldı.");
                byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, sendKey, sendPrefix, sendSequence, Crypto.encode(frame));
                byte[] data = ByteBuffer.allocate(8 + ciphertext.length).putLong(sendSequence).put(ciphertext).array();
                outgoing = new Protocol.Frame("SECURE", "", "", data);
            }
            byte[] wire = Crypto.encode(outgoing);
            synchronized (output) { output.write(wire); output.flush(); }
            sentBytes += wire.length;
            if (secure()) sendSequence++;
        } catch (GeneralSecurityException | IOException exception) {
            failed = true; throw new IOException("Mesaj gönderilemedi; yeniden bağlanın.", exception);
        }
    }

    public Protocol.Frame read() throws IOException {
        if (failed) throw new IOException("Kanal kullanılamıyor.");
        try {
            Protocol.Frame incoming = Protocol.read(input);
            receivedBytes += Crypto.encode(incoming).length;
            if (!secure()) return incoming;
            if (!incoming.type().equals("SECURE") || !incoming.sender().isEmpty() || !incoming.text().isEmpty()
                    || incoming.data().length < 24 || receiveSequence == Long.MAX_VALUE)
                throw new IOException("Şifreli mesaj bekleniyordu.");
            ByteBuffer data = ByteBuffer.wrap(incoming.data());
            if (data.getLong() != receiveSequence) throw new IOException("Tekrarlanan veya sırası bozuk mesaj.");
            byte[] ciphertext = new byte[data.remaining()]; data.get(ciphertext);
            byte[] clear = crypt(Cipher.DECRYPT_MODE, receiveKey, receivePrefix, receiveSequence, ciphertext);
            ByteArrayInputStream decoded = new ByteArrayInputStream(clear);
            Protocol.Frame result = Protocol.read(decoded);
            if (decoded.available() != 0) throw new IOException("Fazladan mesaj verisi.");
            receiveSequence++;
            return result;
        } catch (EOFException exception) {
            failed = true; throw exception;
        } catch (GeneralSecurityException | IOException exception) {
            failed = true; throw new IOException("Mesaj doğrulanamadı veya bağlantı kapandı.", exception);
        }
    }

    private static byte[] crypt(int mode, SecretKey key, byte[] prefix, long sequence, byte[] bytes)
            throws GeneralSecurityException {
        byte[] nonce = ByteBuffer.allocate(12).put(prefix).putLong(sequence).array();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(Crypto.utf8("kriptoloji-v1 SECURE"));
        cipher.updateAAD(nonce);
        return cipher.doFinal(bytes);
    }
}
