package com.applyflow.security;

import com.applyflow.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for stored mailbox credentials. The 256-bit key is derived as SHA-256 of the configured
 * secret. Output format: base64(iv[12] || ciphertext+tag).
 */
@Component
public class CredentialEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public CredentialEncryptor(AppProperties props) {
        this(props.encryption().key());
    }

    public CredentialEncryptor(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Encryption key must not be blank");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to initialise credential encryption", e);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Nothing to encrypt");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
            buf.put(iv).put(ct);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Credential encryption failed");
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("Nothing to decrypt");
        }
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            if (all.length <= IV_LENGTH) {
                throw new IllegalStateException("Stored credential is corrupt");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH));
            byte[] pt = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Deliberately no details: may indicate a changed APP_ENCRYPTION_KEY.
            throw new IllegalStateException("Stored credential could not be decrypted (was APP_ENCRYPTION_KEY changed?)");
        }
    }
}
