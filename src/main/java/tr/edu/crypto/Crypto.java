package tr.edu.crypto;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.*;

// Ders projesinin sabit algoritma parametreleri; agdan gelen parametreleri kullanmayiz.
public final class Crypto {
    public static final List<String> METHODS = List.of("RSA", "DH", "ECDH", "ML-KEM");
    private static final SecureRandom RANDOM = new SecureRandom();
    // RFC 3526, bolum 3: 2048 bit MODP grup 14, g = 2.
    static final BigInteger DH_P = new BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
            "29024E088A67CC74020BBEA63B139B22514A08798E3404DDEF" +
            "9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
            "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
            "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC" +
            "2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
            "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
            "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
            "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C" +
            "9DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
            "15728E5A8AACAA68FFFFFFFFFFFFFFFF", 16);
    static final DHParameterSpec DH_PARAMS = new DHParameterSpec(DH_P, BigInteger.TWO, 256);

    private Crypto() {}

    public static void validateMethod(String method) {
        if (!METHODS.contains(method)) throw new IllegalArgumentException("Geçersiz anahtar paylaşım yöntemi.");
    }

    public static byte[] random(int size) { byte[] bytes = new byte[size]; RANDOM.nextBytes(bytes); return bytes; }
    public static byte[] hash(byte[] bytes) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }
    public static String fingerprint(PublicKey key) throws GeneralSecurityException {
        return HexFormat.of().formatHex(hash(key.getEncoded()));
    }
    public static String normalizeFingerprint(String value) {
        String normalized = value.replace(":", "").replaceAll("\\s", "").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Sunucu ekranındaki 64 haneli SHA-256 parmak izini girin.");
        return normalized;
    }

    public static KeyPair generate(String method) throws GeneralSecurityException {
        validateMethod(method);
        KeyPairGenerator generator = KeyPairGenerator.getInstance(method.equals("ECDH") ? "EC" : method);
        switch (method) {
            case "RSA" -> generator.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4), RANDOM);
            case "DH" -> generator.initialize(DH_PARAMS, RANDOM);
            case "ECDH" -> generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            case "ML-KEM" -> generator.initialize(NamedParameterSpec.ML_KEM_768, RANDOM);
            default -> throw new GeneralSecurityException("Desteklenmeyen yöntem.");
        }
        return generator.generateKeyPair();
    }

    public static PublicKey decode(String method, byte[] bytes) throws GeneralSecurityException {
        validateMethod(method);
        PublicKey key = KeyFactory.getInstance(method.equals("ECDH") ? "EC" : method)
                .generatePublic(new X509EncodedKeySpec(bytes));
        if (method.equals("RSA")) {
            if (!(key instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() != 2048
                    || !rsa.getPublicExponent().equals(RSAKeyGenParameterSpec.F4))
                throw new InvalidKeyException("RSA-2048 anahtarı gerekli.");
        } else if (method.equals("DH")) {
            if (!(key instanceof DHPublicKey dh) || !dh.getParams().getP().equals(DH_P)
                    || !dh.getParams().getG().equals(BigInteger.TWO)
                    || dh.getY().compareTo(BigInteger.TWO) < 0 || dh.getY().compareTo(DH_P.subtract(BigInteger.TWO)) > 0
                    || !dh.getY().modPow(DH_P.subtract(BigInteger.ONE).shiftRight(1), DH_P).equals(BigInteger.ONE))
                throw new InvalidKeyException("Geçersiz DH grup değeri.");
        } else if (method.equals("ECDH")) {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec expected = params.getParameterSpec(ECParameterSpec.class);
            if (!(key instanceof ECPublicKey ec) || !ec.getParams().getCurve().equals(expected.getCurve())
                    || !ec.getParams().getGenerator().equals(expected.getGenerator())
                    || !ec.getParams().getOrder().equals(expected.getOrder())
                    || ec.getParams().getCofactor() != expected.getCofactor())
                throw new InvalidKeyException("ECDH P-256 anahtarı gerekli.");
        } else if (!(key instanceof AsymmetricKey asymmetric)
                || !(asymmetric.getParams() instanceof NamedParameterSpec params)
                || !params.getName().equalsIgnoreCase("ML-KEM-768")) {
            throw new InvalidKeyException("ML-KEM-768 anahtarı gerekli.");
        }
        return key;
    }

    public static byte[] agree(String method, PrivateKey own, PublicKey peer) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance(method);
        agreement.init(own); agreement.doPhase(peer, true);
        return agreement.generateSecret();
    }

    public static byte[] rsa(int mode, Key key, byte[] bytes) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(mode, key, new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        return cipher.doFinal(bytes);
    }

    public static byte[] sign(PrivateKey key, byte[] bytes) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(key); signature.update(bytes); return signature.sign();
    }
    public static void verify(PublicKey key, byte[] bytes, byte[] proof) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initVerify(key); signature.update(bytes);
        if (!signature.verify(proof)) throw new SignatureException("Sunucu imzası doğrulanamadı.");
    }

    // RFC 5869 HKDF-SHA256: yonlere ozel AES anahtarlari ve nonce on ekleri turetilir.
    static byte[] hkdf(byte[] secret, byte[] salt, byte[] info, int length) throws GeneralSecurityException {
        if (length < 1 || length > 255 * 32) throw new IllegalArgumentException("Geçersiz HKDF uzunluğu.");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt.length == 0 ? new byte[32] : salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(secret);
        try {
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] previous = new byte[0];
            for (int index = 1; output.size() < length; index++) {
                mac.update(previous); mac.update(info); previous = mac.doFinal(new byte[]{(byte) index});
                output.writeBytes(previous);
            }
            return Arrays.copyOf(output.toByteArray(), length);
        } finally { Arrays.fill(prk, (byte) 0); }
    }

    static byte[] pack(byte[]... fields) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        for (byte[] field : fields) { out.writeInt(field.length); out.write(field); }
        return buffer.toByteArray();
    }
    static byte[][] unpack(byte[] bytes, int count) throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
        byte[][] fields = new byte[count][];
        for (int index = 0; index < count; index++) {
            int size = input.readInt();
            if (size < 0 || size > input.available()) throw new IOException("Geçersiz anahtar paylaşım verisi.");
            fields[index] = input.readNBytes(size);
        }
        if (input.available() != 0) throw new IOException("Fazladan anahtar paylaşım verisi.");
        return fields;
    }
    static byte[] encode(Protocol.Frame frame) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(); Protocol.write(buffer, frame); return buffer.toByteArray();
    }
    static byte[] utf8(String text) { return text.getBytes(StandardCharsets.UTF_8); }
}
