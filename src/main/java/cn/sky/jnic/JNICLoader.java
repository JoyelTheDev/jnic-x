package cn.sky.jnic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class JNICLoader {

    private static volatile boolean loaded = false;
    private static final Object LOCK = new Object();
    private static final int K0  = 0x00000000, KM0 = 0x00000000;
    private static final int K1  = 0x00000000, KM1 = 0x00000000;
    private static final int K2  = 0x00000000, KM2 = 0x00000000;
    private static final int K3  = 0x00000000, KM3 = 0x00000000;
    private static final int K4  = 0x00000000, KM4 = 0x00000000;
    private static final int K5  = 0x00000000, KM5 = 0x00000000;
    private static final int K6  = 0x00000000, KM6 = 0x00000000;
    private static final int K7  = 0x00000000, KM7 = 0x00000000;
    private static final int N0  = 0x00000000, NM0 = 0x00000000;
    private static final int N1  = 0x00000000, NM1 = 0x00000000;
    private static final int N2  = 0x00000000, NM2 = 0x00000000;
    
    private static int rotl(int v, int n) { return (v << n) | (v >>> (32 - n)); }

    private static byte[] chacha20Block(int[] key, int[] nonce, int counter) {
        int[] s = {
            0x61707865, 
            0x3320646e, 
            0x79622d32, 
            0x6b206574,
            key[0], key[1], 
            key[2], key[3], 
            key[4], key[5], 
            key[6], key[7],
            counter, nonce[0], 
            nonce[1], nonce[2]
        };
        int[] w = s.clone();
        for (int i = 0; i < 10; i++) {
            w[0]+=w[4];  w[12]=rotl(w[12]^w[0],16); w[8]+=w[12];  w[4]=rotl(w[4]^w[8],12);
            w[0]+=w[4];  w[12]=rotl(w[12]^w[0], 8); w[8]+=w[12];  w[4]=rotl(w[4]^w[8], 7);
            w[1]+=w[5];  w[13]=rotl(w[13]^w[1],16); w[9]+=w[13];  w[5]=rotl(w[5]^w[9],12);
            w[1]+=w[5];  w[13]=rotl(w[13]^w[1], 8); w[9]+=w[13];  w[5]=rotl(w[5]^w[9], 7);
            w[2]+=w[6];  w[14]=rotl(w[14]^w[2],16); w[10]+=w[14]; w[6]=rotl(w[6]^w[10],12);
            w[2]+=w[6];  w[14]=rotl(w[14]^w[2], 8); w[10]+=w[14]; w[6]=rotl(w[6]^w[10], 7);
            w[3]+=w[7];  w[15]=rotl(w[15]^w[3],16); w[11]+=w[15]; w[7]=rotl(w[7]^w[11],12);
            w[3]+=w[7];  w[15]=rotl(w[15]^w[3], 8); w[11]+=w[15]; w[7]=rotl(w[7]^w[11], 7);
            w[0]+=w[5];  w[15]=rotl(w[15]^w[0],16); w[10]+=w[15]; w[5]=rotl(w[5]^w[10],12);
            w[0]+=w[5];  w[15]=rotl(w[15]^w[0], 8); w[10]+=w[15]; w[5]=rotl(w[5]^w[10], 7);
            w[1]+=w[6];  w[12]=rotl(w[12]^w[1],16); w[11]+=w[12]; w[6]=rotl(w[6]^w[11],12);
            w[1]+=w[6];  w[12]=rotl(w[12]^w[1], 8); w[11]+=w[12]; w[6]=rotl(w[6]^w[11], 7);
            w[2]+=w[7];  w[13]=rotl(w[13]^w[2],16); w[8]+=w[13];  w[7]=rotl(w[7]^w[8],12);
            w[2]+=w[7];  w[13]=rotl(w[13]^w[2], 8); w[8]+=w[13];  w[7]=rotl(w[7]^w[8], 7);
            w[3]+=w[4];  w[14]=rotl(w[14]^w[3],16); w[9]+=w[14];  w[4]=rotl(w[4]^w[9],12);
            w[3]+=w[4];  w[14]=rotl(w[14]^w[3], 8); w[9]+=w[14];  w[4]=rotl(w[4]^w[9], 7);
        }
        for (int i = 0; i < 16; i++) w[i] += s[i];
        ByteBuffer out = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        for (int word : w) out.putInt(word);
        return out.array();
    }

    private static byte[] chacha20Decrypt(byte[] data, int[] key, int[] nonce) {
        byte[] out = new byte[data.length];
        int counter = 0, pos = 0;
        while (pos < data.length) {
            byte[] stream = chacha20Block(key, nonce, counter++);
            int len = Math.min(64, data.length - pos);
            for (int i = 0; i < len; i++) out[pos + i] = (byte)(data[pos + i] ^ stream[i]);
            pos += len;
        }
        return out;
    }

    private static int[] rebuildKey() {
        return new int[]{ K0^KM0, K1^KM1, K2^KM2, K3^KM3,
                          K4^KM4, K5^KM5, K6^KM6, K7^KM7 };
    }

    private static int[] rebuildNonce() {
        return new int[]{ N0^NM0, N1^NM1, N2^NM2 };
    }

    public static void load(String libName, Class<?> clazz) {
        if (!loaded) {
            synchronized (LOCK) {
                if (!loaded) {
                    try {
                        String os   = System.getProperty("os.name").toLowerCase();
                        String arch = System.getProperty("os.arch").toLowerCase();
                        String platform, ext;

                        if (os.contains("win")) {
                            platform = "windows"; ext = ".dll";
                        } else if (os.contains("mac")) {
                            platform = "macos"; ext = ".dylib";
                        } else {
                            platform = "linux-gnu"; ext = ".so";
                            if (System.getProperty("java.vendor", "").toLowerCase().contains("android"))
                                platform = "android";
                        }

                        if (arch.contains("64") && !arch.contains("aarch64")) arch = "x86_64";
                        else if (arch.equals("aarch64")) { /* keep */ }
                        else if (arch.contains("arm")) arch = "arm";
                        else arch = "x86";

                        String targetName = "lib" + libName + "_" + arch + "-" + platform + ext;
                        byte[] encrypted;
                        try (InputStream is = JNICLoader.class.getResourceAsStream(
                                "/cn/sky/jnic/000000000000000000000000000000000000.dat")) {
                            if (is == null) throw new UnsatisfiedLinkError("Native blob missing");
                            ByteArrayOutputStream buf = new ByteArrayOutputStream();
                            byte[] tmp = new byte[8192]; int n;
                            while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
                            encrypted = buf.toByteArray();
                        }

                        byte[] plain = chacha20Decrypt(encrypted, rebuildKey(), rebuildNonce());
                        ByteBuffer buf = ByteBuffer.wrap(plain);
                        int count = buf.getInt();
                        boolean found = false;

                        for (int i = 0; i < count; i++) {
                            int nameLen = buf.getInt();
                            byte[] nameBytes = new byte[nameLen];
                            buf.get(nameBytes);
                            String name = new String(nameBytes, StandardCharsets.UTF_8);
                            int contentLen = buf.getInt();

                            if (!found && name.equals(targetName)) {
                                byte[] content = new byte[contentLen];
                                buf.get(content);
                                File tempFile = new File(
                                    System.getProperty("java.io.tmpdir"),
                                    UUID.randomUUID() + ".tmp");
                                try (OutputStream os2 = new FileOutputStream(tempFile)) {
                                    os2.write(content);
                                }
                                System.load(tempFile.getAbsolutePath());
                                tempFile.delete();
                                found = true;
                            } else {
                                buf.position(buf.position() + contentLen);
                            }
                        }

                        if (!found) throw new UnsatisfiedLinkError("No native lib for " + targetName);
                        loaded = true;

                    } catch (Exception e) {
                        throw new RuntimeException("Failed to load native library", e);
                    }
                }
            }
        }
        registerNatives(clazz);
    }

    private static native void registerNatives(Class<?> clazz);
}
