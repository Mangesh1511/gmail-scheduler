package com.example.scheduler.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class TokenEncryptionService {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final String encodedKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenEncryptionService(@Value("${app.token-encryption-key:}") String encodedKey) {
        this.encodedKey = encodedKey;
    }

    public String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce).put(ciphertext).array());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt Gmail credential", exception);
        }
    }

    public String decrypt(String encrypted) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encrypted);
            if (bytes.length <= NONCE_BYTES) throw new IllegalArgumentException("Invalid encrypted credential");
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt Gmail credential", exception);
        }
    }

    private SecretKeySpec key() {
        byte[] decoded = Base64.getDecoder().decode(encodedKey);
        if (decoded.length != 32) throw new IllegalStateException("TOKEN_ENCRYPTION_KEY must decode to exactly 32 bytes");
        return new SecretKeySpec(decoded, "AES");
    }
}
