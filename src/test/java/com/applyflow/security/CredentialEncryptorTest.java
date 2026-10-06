package com.applyflow.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialEncryptorTest {

    @Test
    void roundTrip() {
        CredentialEncryptor enc = new CredentialEncryptor("test-key");
        String secret = "abcd efgh ijkl mnop";
        String cipher = enc.encrypt(secret);
        assertThat(cipher).doesNotContain("abcd");
        assertThat(enc.decrypt(cipher)).isEqualTo(secret);
    }

    @Test
    void randomIvMakesCiphertextsDiffer() {
        CredentialEncryptor enc = new CredentialEncryptor("test-key");
        assertThat(enc.encrypt("same")).isNotEqualTo(enc.encrypt("same"));
    }

    @Test
    void wrongKeyFailsWithoutLeakingDetails() {
        String cipher = new CredentialEncryptor("key-a").encrypt("secret");
        assertThatThrownBy(() -> new CredentialEncryptor("key-b").decrypt(cipher))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("secret");
    }

    @Test
    void tamperedCiphertextIsRejected() {
        CredentialEncryptor enc = new CredentialEncryptor("k");
        String cipher = enc.encrypt("secret");
        char[] chars = cipher.toCharArray();
        int i = chars.length / 2; // a ciphertext byte (avoids unused base64 padding bits at the end)
        chars[i] = chars[i] == 'A' ? 'B' : 'A';
        assertThatThrownBy(() -> enc.decrypt(new String(chars))).isInstanceOf(IllegalStateException.class);
    }
}
