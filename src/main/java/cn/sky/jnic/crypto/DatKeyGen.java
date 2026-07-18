package cn.sky.jnic.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;

public final class DatKeyGen {

    private DatKeyGen() {}

    public record KeyMaterial(
        byte[] key,
        byte[] nonce,
        int[]  keyParts,
        int[]  keyMasks,
        int[]  nonceParts,
        int[]  nonceMasks
    ) {}

    public static KeyMaterial generate(UUID buildId) {
        byte[] seed = buildId.toString().getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            md.update(seed);
            md.update((byte) 0x4B);
            byte[] kHash = md.digest();

            md.reset();
            md.update(seed);
            md.update((byte) 0x4E);
            byte[] nHash = md.digest();

            byte[] key   = kHash;
            byte[] nonce = new byte[12];
            System.arraycopy(nHash, 0, nonce, 0, 12);

            SecureRandom rng = new SecureRandom();
            int[] keyMasks   = new int[8];
            int[] nonceMasks = new int[3];
            for (int i = 0; i < 8; i++) keyMasks[i]   = rng.nextInt();
            for (int i = 0; i < 3; i++) nonceMasks[i] = rng.nextInt();

            int[] keyParts = new int[8];
            for (int i = 0; i < 8; i++) {
                int word = ((key[i*4]   & 0xFF))
                         | ((key[i*4+1] & 0xFF) <<  8)
                         | ((key[i*4+2] & 0xFF) << 16)
                         | ((key[i*4+3] & 0xFF) << 24);
                keyParts[i] = word ^ keyMasks[i];
            }

            int[] nonceParts = new int[3];
            for (int i = 0; i < 3; i++) {
                int word = ((nonce[i*4]   & 0xFF))
                         | ((nonce[i*4+1] & 0xFF) <<  8)
                         | ((nonce[i*4+2] & 0xFF) << 16)
                         | ((nonce[i*4+3] & 0xFF) << 24);
                nonceParts[i] = word ^ nonceMasks[i];
            }

            return new KeyMaterial(key, nonce, keyParts, keyMasks, nonceParts, nonceMasks);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
