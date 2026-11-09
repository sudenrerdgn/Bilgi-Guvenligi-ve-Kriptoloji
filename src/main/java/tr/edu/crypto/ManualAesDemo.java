package tr.edu.crypto;

import java.util.Arrays;
import java.util.HexFormat;

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
            System.out.println("Acik blok: " + hex.formatHex(input));
            System.out.println("Anahtar genisletme (K0..K10):");
            byte[][] keys = aes.roundKeys();
            for (int round = 0; round < keys.length; round++)
                System.out.printf("K%02d: %s%n", round, hex.formatHex(keys[round]));
            System.out.println("Tur islemleri (sutun oncelikli state):");
            for (ManualAes128.Step step : aes.traceEncryption(input))
                System.out.printf("%2d %-12s %s%n", step.round(), step.operation(), step.stateHex());
            byte[] encrypted = aes.encryptBlock(input), decrypted = aes.decryptBlock(encrypted);
            System.out.println("Sifreli: " + hex.formatHex(encrypted));
            System.out.println("Cozulen: " + hex.formatHex(decrypted));
            if (!Arrays.equals(input, decrypted)) throw new IllegalStateException("Çözme sonucu uyuşmadı.");
            if (args.length == 0 && !hex.formatHex(encrypted).equals("69c4e0d86a7b0430d8cdb78070b4c55a"))
                throw new IllegalStateException("FIPS 197 sonucu uyuşmadı.");
            System.out.println("Dogrulama basarili.");
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        }
    }
}
