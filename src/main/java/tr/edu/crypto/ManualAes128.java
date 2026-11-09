package tr.edu.crypto;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

// FIPS 197 AES-128 cekirdegi. Kriptografi API'si cagirmaz; tek 16 bayt blok isler.
public final class ManualAes128 {
    public static final int BLOCK_BYTES = 16;
    private static final int ROUNDS = 10;
    private static final int[] S_BOX = {
        0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
        0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
        0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
        0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
        0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
        0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
        0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
        0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
        0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
        0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
        0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
        0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
        0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
        0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
        0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
        0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16
    };
    private static final int[] INVERSE_S_BOX = new int[256];
    static {
        for (int index = 0; index < S_BOX.length; index++) INVERSE_S_BOX[S_BOX[index]] = index;
    }
    private final byte[][] roundKeys;

    public ManualAes128(byte[] key) {
        requireBlock(key, "Anahtar");
        roundKeys = expandKey(key);
    }

    // Degismez hex kopyalari: ders icin ara durumlar, gercek oturum anahtarlari icin degil.
    public record Step(int round, String operation, String stateHex) {}

    public byte[] encryptBlock(byte[] plaintext) { return encrypt(plaintext, null); }

    public List<Step> traceEncryption(byte[] plaintext) {
        List<Step> steps = new ArrayList<>();
        encrypt(plaintext, steps);
        return List.copyOf(steps);
    }

    public byte[][] roundKeys() {
        byte[][] copy = new byte[roundKeys.length][];
        for (int index = 0; index < copy.length; index++) copy[index] = roundKeys[index].clone();
        return copy;
    }

    private byte[] encrypt(byte[] plaintext, List<Step> steps) {
        requireBlock(plaintext, "Açık metin");
        byte[] state = plaintext.clone();
        addRoundKey(state, roundKeys[0]);
        record(steps, 0, "AddRoundKey", state);
        for (int round = 1; round <= ROUNDS; round++) {
            subBytes(state, S_BOX);
            record(steps, round, "SubBytes", state);
            shiftRows(state, false);
            record(steps, round, "ShiftRows", state);
            if (round != ROUNDS) {
                mixColumns(state, false);
                record(steps, round, "MixColumns", state);
            }
            addRoundKey(state, roundKeys[round]);
            record(steps, round, "AddRoundKey", state);
        }
        return state;
    }

    public byte[] decryptBlock(byte[] ciphertext) {
        requireBlock(ciphertext, "Şifreli metin");
        byte[] state = ciphertext.clone();
        addRoundKey(state, roundKeys[ROUNDS]);
        for (int round = ROUNDS - 1; round >= 1; round--) {
            shiftRows(state, true);
            subBytes(state, INVERSE_S_BOX);
            addRoundKey(state, roundKeys[round]);
            mixColumns(state, true);
        }
        shiftRows(state, true);
        subBytes(state, INVERSE_S_BOX);
        addRoundKey(state, roundKeys[0]);
        return state;
    }

    private static byte[][] expandKey(byte[] key) {
        // 4 baslangic kelimesi -> 44 kelime = 176 bayt = 11 tur anahtari.
        byte[] expanded = new byte[BLOCK_BYTES * (ROUNDS + 1)];
        System.arraycopy(key, 0, expanded, 0, BLOCK_BYTES);
        int rcon = 1;
        for (int offset = BLOCK_BYTES; offset < expanded.length; offset += 4) {
            int[] word = new int[4];
            for (int index = 0; index < 4; index++) word[index] = expanded[offset - 4 + index] & 0xff;
            if (offset % BLOCK_BYTES == 0) {
                // RotWord, SubWord, ardindan ilk bayta Rcon XOR.
                int first = word[0];
                word[0] = S_BOX[word[1]] ^ rcon;
                word[1] = S_BOX[word[2]];
                word[2] = S_BOX[word[3]];
                word[3] = S_BOX[first];
                rcon = multiply(rcon, 2);
            }
            for (int index = 0; index < 4; index++)
                expanded[offset + index] = (byte) (expanded[offset - BLOCK_BYTES + index] ^ word[index]);
        }
        byte[][] keys = new byte[ROUNDS + 1][BLOCK_BYTES];
        for (int round = 0; round <= ROUNDS; round++)
            System.arraycopy(expanded, round * BLOCK_BYTES, keys[round], 0, BLOCK_BYTES);
        return keys;
    }

    private static void subBytes(byte[] state, int[] table) {
        for (int index = 0; index < BLOCK_BYTES; index++) state[index] = (byte) table[state[index] & 0xff];
    }

    private static void shiftRows(byte[] state, boolean inverse) {
        // State sutun onceliklidir: state[4 * column + row].
        byte[] before = state.clone();
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                int sourceColumn = (column + (inverse ? 4 - row : row)) % 4;
                state[4 * column + row] = before[4 * sourceColumn + row];
            }
        }
    }

    private static void mixColumns(byte[] state, boolean inverse) {
        int[] factors = inverse ? new int[]{14, 11, 13, 9} : new int[]{2, 3, 1, 1};
        for (int column = 0; column < 4; column++) {
            int start = 4 * column;
            byte[] before = {state[start], state[start + 1], state[start + 2], state[start + 3]};
            for (int row = 0; row < 4; row++) {
                int value = 0;
                for (int index = 0; index < 4; index++)
                    value ^= multiply(before[index] & 0xff, factors[(index - row + 4) % 4]);
                state[start + row] = (byte) value;
            }
        }
    }

    private static int multiply(int value, int factor) {
        // GF(2^8): x^8 + x^4 + x^3 + x + 1, indirgeme polinomu 0x11b.
        int result = 0;
        while (factor != 0) {
            if ((factor & 1) != 0) result ^= value;
            value = ((value << 1) ^ ((value & 0x80) != 0 ? 0x11b : 0)) & 0xff;
            factor >>>= 1;
        }
        return result;
    }

    private static void addRoundKey(byte[] state, byte[] key) {
        for (int index = 0; index < BLOCK_BYTES; index++) state[index] ^= key[index];
    }

    private static void record(List<Step> steps, int round, String operation, byte[] state) {
        if (steps != null) steps.add(new Step(round, operation, HexFormat.of().formatHex(state)));
    }

    private static void requireBlock(byte[] bytes, String name) {
        if (bytes == null || bytes.length != BLOCK_BYTES)
            throw new IllegalArgumentException(name + " tam 16 bayt olmalı.");
    }
}
