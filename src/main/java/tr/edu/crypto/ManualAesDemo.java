package tr.edu.crypto;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

// Yalnizca ders ornekleri: tur anahtarlarini ve ara durumlari terminale yazar.
public final class ManualAesDemo {
    public static void main(String[] args) {
        try {
            if (args.length != 0 && args.length != 2)
                throw new IllegalArgumentException("Kullanım: ManualAesDemo [32-haneli-hex-anahtar 32-haneli-hex-blok]");
            HexFormat hex = HexFormat.of();
            byte[] key = hex.parseHex(args.length == 0 ? "000102030405060708090a0b0c0d0e0f" : args[0]);
            byte[] input = hex.parseHex(args.length == 0 ? "00112233445566778899aabbccddeeff" : args[1]);
            ManualAes128 aes = new ManualAes128(key);
            System.out.println("Manuel AES-128 | 16 bayt blok | 10 tur | Ders ornegi");
            System.out.println("Anahtar:   " + hex.formatHex(key));
            System.out.println("Acik blok: " + hex.formatHex(input));
            System.out.println("Anahtar genisletme (K0..K10):");
            byte[][] keys = aes.roundKeys();
            for (int round = 0; round < keys.length; round++)
                System.out.printf("K%02d: %s%n", round, hex.formatHex(keys[round]));
            System.out.println("Tur islemleri (sutun oncelikli state):");
            for (ManualAes128.Step step : aes.traceEncryption(input))
                System.out.printf("%2d %-12s %s%n", step.round(), step.operation(), step.stateHex());
            byte[] encrypted = aes.encryptBlock(input), decrypted = aes.decryptBlock(encrypted);

            // JCE sadece karsilastirma referansidir; manuel cekirdek API cagirmaz.
            Cipher reference = Cipher.getInstance("AES/ECB/NoPadding");
            SecretKeySpec referenceKey = new SecretKeySpec(key, "AES");
            reference.init(Cipher.ENCRYPT_MODE, referenceKey);
            byte[] libraryEncrypted = reference.doFinal(input);
            reference.init(Cipher.DECRYPT_MODE, referenceKey);
            // Cozme karsilastirmasinda iki uygulamaya da ayni sifreli blok verilir.
            byte[] libraryDecrypted = reference.doFinal(encrypted);
            boolean encryptionMatches = Arrays.equals(encrypted, libraryEncrypted);
            boolean decryptionMatches = Arrays.equals(decrypted, libraryDecrypted);
            boolean originalMatches = Arrays.equals(input, decrypted) && Arrays.equals(input, libraryDecrypted);

            System.out.println();
            System.out.println("Kutuphane karsilastirmasi: " + reference.getAlgorithm()
                    + " | Saglayici: " + reference.getProvider().getName());
            System.out.println("Ayni 128 bit anahtar ve 16 bayt blok; padding yok.");
            System.out.printf("%-12s %-32s | %-32s%n", "Islem", "Manuel AES", "Java kutuphanesi");
            System.out.printf("%-12s %s | %s%n", "Sifreleme", hex.formatHex(encrypted), hex.formatHex(libraryEncrypted));
            System.out.printf("%-12s %s | %s%n", "Cozme", hex.formatHex(decrypted), hex.formatHex(libraryDecrypted));
            System.out.println("Sifreleme sonuclari: " + (encryptionMatches ? "ESLESTI" : "ESLESMEDI"));
            System.out.println("Cozme sonuclari: " + (decryptionMatches ? "ESLESTI" : "ESLESMEDI"));
            System.out.println("Orijinal blok geri elde edildi: " + (originalMatches ? "EVET" : "HAYIR"));
            if (!encryptionMatches || !decryptionMatches || !originalMatches)
                throw new IllegalStateException("Manuel AES ve kütüphane karşılaştırması başarısız.");
            if (hex.formatHex(key).equals("000102030405060708090a0b0c0d0e0f")
                    && hex.formatHex(input).equals("00112233445566778899aabbccddeeff")
                    && !hex.formatHex(encrypted).equals("69c4e0d86a7b0430d8cdb78070b4c55a"))
                throw new IllegalStateException("FIPS 197 sonucu uyuşmadı.");
            System.out.println("Dogrulama basarili.");
        } catch (IllegalArgumentException | IllegalStateException | GeneralSecurityException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        }
    }
}
