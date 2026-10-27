package com.literag.security;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码哈希：PBKDF2-HMAC-SHA256，随机盐，格式 pbkdf2$迭代次数$盐$哈希。
 * 单次约几十毫秒，能显著抬高离线爆破成本；不引入 spring-security-crypto 以保持依赖精简。
 */
@Component
public class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final SecureRandom random = new SecureRandom();

    public String hash(String raw) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] key = derive(raw, salt, ITERATIONS);
        Base64.Encoder enc = Base64.getEncoder();
        return "pbkdf2$" + ITERATIONS + "$" + enc.encodeToString(salt) + "$" + enc.encodeToString(key);
    }

    public boolean matches(String raw, String stored) {
        if (raw == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        int iterations;
        try {
            iterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        Base64.Decoder dec = Base64.getDecoder();
        byte[] salt = dec.decode(parts[2]);
        byte[] expected = dec.decode(parts[3]);
        byte[] actual = derive(raw, salt, iterations);
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] derive(String raw, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(raw.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("密码哈希计算失败", e);
        }
    }
}
