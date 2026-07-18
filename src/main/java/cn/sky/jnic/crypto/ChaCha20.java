package cn.sky.jnic.crypto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class ChaCha20 {

    private ChaCha20() {}

    private static int rotl(int v, int n) {
        return (v << n) | (v >>> (32 - n));
    }

    private static void quarterRound(int[] s, int a, int b, int c, int d) {
        s[a] += s[b]; s[d] ^= s[a]; s[d] = rotl(s[d], 16);
        s[c] += s[d]; s[b] ^= s[c]; s[b] = rotl(s[b], 12);
        s[a] += s[b]; s[d] ^= s[a]; s[d] = rotl(s[d],  8);
        s[c] += s[d]; s[b] ^= s[c]; s[b] = rotl(s[b],  7);
    }

    private static byte[] block(int[] key, int[] nonce, int counter) {
        int[] s = new int[16];
        s[0]  = 0x61707865; s[1]  = 0x3320646e;
        s[2]  = 0x79622d32; s[3]  = 0x6b206574;
        for (int i = 0; i < 8; i++) s[4 + i] = key[i];
        s[12] = counter;
        s[13] = nonce[0]; s[14] = nonce[1]; s[15] = nonce[2];

        int[] working = s.clone();
        for (int i = 0; i < 10; i++) {
            quarterRound(working, 0, 4,  8, 12);
            quarterRound(working, 1, 5,  9, 13);
            quarterRound(working, 2, 6, 10, 14);
            quarterRound(working, 3, 7, 11, 15);
            quarterRound(working, 0, 5, 10, 15);
            quarterRound(working, 1, 6, 11, 12);
            quarterRound(working, 2, 7,  8, 13);
            quarterRound(working, 3, 4,  9, 14);
        }
        for (int i = 0; i < 16; i++) working[i] += s[i];

        ByteBuffer out = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        for (int w : working) out.putInt(w);
        return out.array();
    }

    public static byte[] crypt(byte[] data, byte[] key, byte[] nonce) {
        if (key.length != 32) throw new IllegalArgumentException("Key must be 32 bytes");
        if (nonce.length != 12) throw new IllegalArgumentException("Nonce must be 12 bytes");

        int[] keyWords   = toWords(key,   8);
        int[] nonceWords = toWords(nonce, 3);

        byte[] out = new byte[data.length];
        int counter = 0;
        int pos = 0;

        while (pos < data.length) {
            byte[] stream = block(keyWords, nonceWords, counter++);
            int len = Math.min(64, data.length - pos);
            for (int i = 0; i < len; i++) {
                out[pos + i] = (byte) (data[pos + i] ^ stream[i]);
            }
            pos += len;
        }
        return out;
    }

    private static int[] toWords(byte[] b, int wordCount) {
        int[] words = new int[wordCount];
        ByteBuffer buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < wordCount; i++) words[i] = buf.getInt();
        return words;
    }
}
