package tr.edu.crypto;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public final class ManualAes128Test {
    private static final HexFormat HEX = HexFormat.of();
    private static int checks;

    public static void main(String[] args) throws Exception {
        // FIPS 197 (2001), Ek C.1: bilinen sonuc ve ara degerler.
        byte[] key = hex("000102030405060708090a0b0c0d0e0f");
        byte[] plain = hex("00112233445566778899aabbccddeeff");
        byte[] expected = hex("69c4e0d86a7b0430d8cdb78070b4c55a");
        ManualAes128 aes = new ManualAes128(key);
        equal(aes.encryptBlock(plain), expected, "FIPS 197 sifreleme");
        equal(aes.decryptBlock(expected), plain, "FIPS 197 cozme");
        String[] expectedKeys = {
            "000102030405060708090a0b0c0d0e0f", "d6aa74fdd2af72fadaa678f1d6ab76fe",
            "b692cf0b643dbdf1be9bc5006830b3fe", "b6ff744ed2c2c9bf6c590cbf0469bf41",
            "47f7f7bc95353e03f96c32bcfd058dfd", "3caaa3e8a99f9deb50f3af57adf622aa",
            "5e390f7df7a69296a7553dc10aa31f6b", "14f9701ae35fe28c440adf4d4ea9c026",
            "47438735a41c65b9e016baf4aebf7ad2", "549932d1f08557681093ed9cbe2c974e",
            "13111d7fe3944a17f307a78b4d2b30c5"
        };
        byte[][] keys = aes.roundKeys();
        check(keys.length == 11, "11 tur anahtari");
        for (int round = 0; round < keys.length; round++)
            equal(keys[round], hex(expectedKeys[round]), "Tur anahtari " + round);
        List<ManualAes128.Step> trace = aes.traceEncryption(plain);
        String[] firstStates = {
            "00102030405060708090a0b0c0d0e0f0", "63cab7040953d051cd60e0e7ba70e18c",
            "6353e08c0960e104cd70b751bacad0e7", "5f72641557f5bc92f7be3b291db9f91a",
            "89d810e8855ace682d1843d8cb128fe4"
        };
        String[] operations = {"AddRoundKey", "SubBytes", "ShiftRows", "MixColumns", "AddRoundKey"};
        for (int index = 0; index < firstStates.length; index++) {
            check(trace.get(index).stateHex().equals(firstStates[index])
                    && trace.get(index).operation().equals(operations[index]), "FIPS ara adim " + operations[index]);
        }
        check(trace.size() == 40 && trace.stream().filter(step -> step.operation().equals("MixColumns")).count() == 9
                && trace.stream().noneMatch(step -> step.round() == 10 && step.operation().equals("MixColumns")),
                "Tur sayisi ve son turda MixColumns olmamasi");
        check(trace.getLast().stateHex().equals(HEX.formatHex(expected)), "Izleme ve sifreleme ayni sonuc");
        // NIST SP 800-38A, F.1.1/F.1.2: dort bagimsiz AES-128 blok ornegi.
        ManualAes128 nist = new ManualAes128(hex("2b7e151628aed2a6abf7158809cf4f3c"));
        String[] inputs = {"6bc1bee22e409f96e93d7e117393172a", "ae2d8a571e03ac9c9eb76fac45af8e51",
                "30c81c46a35ce411e5fbc1191a0a52ef", "f69f2445df4f9b17ad2b417be66c3710"};
        String[] outputs = {"3ad77bb40d7a3660a89ecaf32466ef97", "f5d3d58503b9699de785895a96fdbaaf",
                "43b1cd7f598ece23881b00e3ed030688", "7b0c785e27e8ad3f8223207104725dd4"};
        for (int index = 0; index < inputs.length; index++) {
            equal(nist.encryptBlock(hex(inputs[index])), hex(outputs[index]), "NIST blok sifreleme " + index);
            equal(nist.decryptBlock(hex(outputs[index])), hex(inputs[index]), "NIST blok cozme " + index);
        }
        // JCE sadece bagimsiz test referansi; manuel sinif bu API'yi kullanmaz.
        Random random = new Random(128);
        Cipher reference = Cipher.getInstance("AES/ECB/NoPadding");
        for (int trial = 0; trial < 1000; trial++) {
            byte[] randomKey = new byte[16], block = new byte[16];
            random.nextBytes(randomKey); random.nextBytes(block);
            ManualAes128 manual = new ManualAes128(randomKey);
            reference.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(randomKey, "AES"));
            equal(manual.encryptBlock(block), reference.doFinal(block), "JCE sifreleme " + trial);
            reference.init(Cipher.DECRYPT_MODE, new SecretKeySpec(randomKey, "AES"));
            equal(manual.decryptBlock(block), reference.doFinal(block), "JCE bagimsiz sifreli blok cozme " + trial);
        }
        System.out.println("OK: 1000 farkli anahtar/blokta Java ile sifreleme ve cozme ayni.");
        for (int length : new int[]{0, 15, 17, 24, 32}) {
            reject(() -> new ManualAes128(new byte[length]));
            reject(() -> aes.encryptBlock(new byte[length]));
            reject(() -> aes.decryptBlock(new byte[length]));
        }
        reject(() -> new ManualAes128(null));
        reject(() -> aes.encryptBlock(null));
        reject(() -> aes.decryptBlock(null));
        reject(() -> aes.traceEncryption(new byte[15]));
        equal(plain, hex("00112233445566778899aabbccddeeff"), "Girdi degismedi");
        equal(key, hex(expectedKeys[0]), "Anahtar girdisi degismedi");
        equal(expected, hex("69c4e0d86a7b0430d8cdb78070b4c55a"), "Sifreli girdi degismedi");
        Arrays.fill(key, (byte) 0);
        Arrays.fill(keys[0], (byte) 0);
        keys[1] = new byte[16];
        equal(aes.encryptBlock(plain), expected, "Disaridan degisiklik ic anahtarlari etkilemiyor");
        System.out.println("BASARILI: " + checks + " manuel AES kontrolu tamamlandi.");
    }

    private static byte[] hex(String text) { return HEX.parseHex(text); }
    private static void equal(byte[] actual, byte[] expected, String message) {
        check(Arrays.equals(actual, expected), message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Gecersiz uzunluk/null reddedilmedi.");
    }
}
