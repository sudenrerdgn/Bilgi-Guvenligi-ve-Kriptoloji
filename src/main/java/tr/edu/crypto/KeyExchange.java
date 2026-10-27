package tr.edu.crypto;

import java.io.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import javax.crypto.*;

public final class KeyExchange {
    private KeyExchange() {}

    public record Measurement(String method, double serverKeyMs, double clientKeyMs, double exchangeMs,
                              long keyPayloadBytes, long sentBytes, long receivedBytes) {
        public Map<String, Object> snapshot() {
            return Map.of("method", method, "serverKeyMs", serverKeyMs, "clientKeyMs", clientKeyMs,
                    "exchangeMs", exchangeMs, "keyPayloadBytes", keyPayloadBytes,
                    "sentBytes", sentBytes, "receivedBytes", receivedBytes, "totalBytes", sentBytes + receivedBytes);
        }
    }

    public static Measurement client(SecureChannel channel, String name, String method, String fingerprint, boolean measure)
            throws IOException {
        Crypto.validateMethod(method);
        String expected = Crypto.normalizeFingerprint(fingerprint);
        byte[] shared = null;
        try {
            Protocol.Frame hello = new Protocol.Frame(measure ? "MEASURE_HELLO" : "SECURE_HELLO", name, method, Crypto.random(32));
            long started = System.nanoTime();
            channel.write(hello);
            Protocol.Frame server = channel.read();
            if (server.type().equals("ERROR")) throw new IOException(server.text());
            if (!server.type().equals("KEY_SERVER") || !server.sender().isEmpty()) throw new IOException("Sunucu anahtar yanıtı geçersiz.");
            byte[][] fields = Crypto.unpack(server.data(), 4);
            if (fields[2].length != 32) throw new IOException("Sunucu rastgele değeri geçersiz.");
            PublicKey identity = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(fields[0]));
            if (!MessageDigest.isEqual(HexFormat.of().parseHex(expected), Crypto.hash(identity.getEncoded())))
                throw new IOException("Sunucu parmak izi eşleşmiyor. Sunucu ekranındaki değeri kontrol edin.");
            Crypto.verify(identity, proof(hello, server.text(), fields[0], fields[1], fields[2]), fields[3]);
            long serverNanos = Long.parseLong(server.text());
            if (serverNanos < 0) throw new IOException("Geçersiz ölçüm süresi.");
            PublicKey peerKey = Crypto.decode(method, fields[1]);
            byte[] response;
            long generationStarted = System.nanoTime();
            long generationNanos;
            switch (method) {
                case "RSA" -> {
                    shared = Crypto.random(16);
                    generationNanos = System.nanoTime() - generationStarted;
                    response = Crypto.rsa(Cipher.ENCRYPT_MODE, peerKey, shared);
                }
                case "DH", "ECDH" -> {
                    KeyPair own = Crypto.generate(method);
                    generationNanos = System.nanoTime() - generationStarted;
                    response = own.getPublic().getEncoded();
                    shared = Crypto.agree(method, own.getPrivate(), peerKey);
                }
                case "ML-KEM" -> {
                    KEM.Encapsulated encapsulated = KEM.getInstance("ML-KEM").newEncapsulator(peerKey).encapsulate();
                    generationNanos = System.nanoTime() - generationStarted;
                    response = encapsulated.encapsulation(); shared = encapsulated.key().getEncoded();
                }
                default -> throw new IOException("Geçersiz yöntem.");
            }
            Protocol.Frame answer = new Protocol.Frame("KEY_CLIENT", "", "", response);
            channel.write(answer);
            byte[] transcript = transcript(hello, server, answer);
            channel.activate(shared, transcript, true);
            channel.write(new Protocol.Frame("FINISHED", "", "", transcript));
            requireFinished(channel.read(), "FINISHED_ACK", transcript);
            return new Measurement(method, serverNanos / 1_000_000.0, generationNanos / 1_000_000.0,
                    (System.nanoTime() - started) / 1_000_000.0, fields[1].length + response.length,
                    channel.sentBytes(), channel.receivedBytes());
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IOException("Güvenli bağlantı kurulamadı: " + exception.getMessage(), exception);
        } finally { if (shared != null) Arrays.fill(shared, (byte) 0); }
    }

    public static void server(SecureChannel channel, Protocol.Frame hello, KeyPair identity) throws IOException {
        byte[] shared = null;
        try {
            String method = hello.text(); Crypto.validateMethod(method);
            if (hello.data().length != 32) throw new IOException("İstemci rastgele değeri geçersiz.");
            long start = System.nanoTime();
            KeyPair ephemeral = Crypto.generate(method);
            String nanos = Long.toString(System.nanoTime() - start);
            byte[] identityKey = identity.getPublic().getEncoded(), publicKey = ephemeral.getPublic().getEncoded(), nonce = Crypto.random(32);
            byte[] signature = Crypto.sign(identity.getPrivate(), proof(hello, nanos, identityKey, publicKey, nonce));
            Protocol.Frame server = new Protocol.Frame("KEY_SERVER", "", nanos, Crypto.pack(identityKey, publicKey, nonce, signature));
            channel.write(server);
            Protocol.Frame answer = channel.read();
            if (!answer.type().equals("KEY_CLIENT") || !answer.sender().isEmpty() || !answer.text().isEmpty())
                throw new IOException("İstemci anahtar yanıtı geçersiz.");
            shared = switch (method) {
                case "RSA" -> Crypto.rsa(Cipher.DECRYPT_MODE, ephemeral.getPrivate(), answer.data());
                case "DH", "ECDH" -> Crypto.agree(method, ephemeral.getPrivate(), Crypto.decode(method, answer.data()));
                case "ML-KEM" -> KEM.getInstance("ML-KEM").newDecapsulator(ephemeral.getPrivate()).decapsulate(answer.data()).getEncoded();
                default -> throw new IOException("Geçersiz yöntem.");
            };
            if (method.equals("RSA") && shared.length != 16) throw new IOException("AES-128 anahtarı gerekli.");
            byte[] transcript = transcript(hello, server, answer);
            channel.activate(shared, transcript, false);
            requireFinished(channel.read(), "FINISHED", transcript);
            channel.write(new Protocol.Frame("FINISHED_ACK", "", "", transcript));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IOException("Anahtar paylaşımı doğrulanamadı.", exception);
        } finally { if (shared != null) Arrays.fill(shared, (byte) 0); }
    }

    private static byte[] proof(Protocol.Frame hello, String nanos, byte[] identity, byte[] publicKey, byte[] nonce) throws IOException {
        return Crypto.pack(Crypto.utf8("kriptoloji-v1 server identity"), Crypto.encode(hello), Crypto.utf8(nanos), identity, publicKey, nonce);
    }
    private static byte[] transcript(Protocol.Frame hello, Protocol.Frame server, Protocol.Frame answer)
            throws IOException, GeneralSecurityException {
        return Crypto.hash(Crypto.pack(Crypto.encode(hello), Crypto.encode(server), Crypto.encode(answer)));
    }
    private static void requireFinished(Protocol.Frame frame, String type, byte[] transcript) throws IOException {
        if (!frame.type().equals(type) || !frame.sender().isEmpty() || !frame.text().isEmpty()
                || !MessageDigest.isEqual(frame.data(), transcript)) throw new IOException("Anahtar doğrulama onayı geçersiz.");
    }
}
