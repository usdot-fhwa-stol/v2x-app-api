package usdot.v2x.app.api.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import usdot.v2x.app.api.config.crypto.CryptoProperties;

class AesGcmEncryptorTest {

    private static final String KEY_A = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes());
    private static final String KEY_B = Base64.getEncoder().encodeToString(
            "fedcba9876543210fedcba9876543210".getBytes());

    private static AesGcmEncryptor encryptor(String key, int version) {
        CryptoProperties properties = new CryptoProperties();
        properties.setEncryptionKey(key);
        properties.setKeyVersion(version);
        return new AesGcmEncryptor(properties);
    }

    @Test
    void roundTripsValue() {
        AesGcmEncryptor encryptor = encryptor(KEY_A, 1);

        String encrypted = encryptor.encrypt("s3cr3t-päss");

        assertTrue(encrypted.startsWith("v1:"));
        assertFalse(encrypted.contains("s3cr3t"));
        assertEquals("s3cr3t-päss", encryptor.decrypt(encrypted));
    }

    @Test
    void roundTripsEmptyString() {
        AesGcmEncryptor encryptor = encryptor(KEY_A, 1);

        assertEquals("", encryptor.decrypt(encryptor.encrypt("")));
    }

    @Test
    void usesUniqueIvPerEncryption() {
        AesGcmEncryptor encryptor = encryptor(KEY_A, 1);

        assertNotEquals(encryptor.encrypt("same"), encryptor.encrypt("same"));
    }

    @Test
    void passesNullThrough() {
        AesGcmEncryptor encryptor = encryptor(KEY_A, 1);

        assertNull(encryptor.encrypt(null));
        assertNull(encryptor.decrypt(null));
    }

    @Test
    void rejectsTamperedCiphertext() {
        AesGcmEncryptor encryptor = encryptor(KEY_A, 1);
        String encrypted = encryptor.encrypt("value");
        byte[] payload = Base64.getDecoder().decode(encrypted.substring(3));
        payload[payload.length - 1] ^= 0x01;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(payload);

        assertThrows(CredentialEncryptionUnavailableException.class, () -> encryptor.decrypt(tampered));
    }

    @Test
    void rejectsWrongKey() {
        String encrypted = encryptor(KEY_A, 1).encrypt("value");

        assertThrows(CredentialEncryptionUnavailableException.class,
                () -> encryptor(KEY_B, 1).decrypt(encrypted));
    }

    @Test
    void rejectsMismatchedKeyVersion() {
        String encrypted = encryptor(KEY_A, 1).encrypt("value");

        CredentialEncryptionUnavailableException e = assertThrows(CredentialEncryptionUnavailableException.class,
                () -> encryptor(KEY_A, 2).decrypt(encrypted));
        assertTrue(e.getReason().contains("key version 1"));
    }

    @Test
    void rejectsMalformedValues() {
        AesGcmEncryptor encryptor = encryptor(KEY_A, 1);

        assertThrows(CredentialEncryptionUnavailableException.class, () -> encryptor.decrypt("plaintext"));
        assertThrows(CredentialEncryptionUnavailableException.class, () -> encryptor.decrypt("v1:not*base64"));
        assertThrows(CredentialEncryptionUnavailableException.class, () -> encryptor.decrypt("v1:AAAA"));
    }

    @Test
    void missingKeyDisablesEncryptionWith503() {
        AesGcmEncryptor encryptor = encryptor("  ", 1);

        assertFalse(encryptor.isConfigured());
        CredentialEncryptionUnavailableException e = assertThrows(CredentialEncryptionUnavailableException.class,
                () -> encryptor.encrypt("value"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        assertThrows(CredentialEncryptionUnavailableException.class, () -> encryptor.decrypt("v1:AAAA"));
    }

    @Test
    void rejectsInvalidKeyAtStartup() {
        assertThrows(IllegalStateException.class, () -> encryptor("not base64!", 1));
        assertThrows(IllegalStateException.class,
                () -> encryptor(Base64.getEncoder().encodeToString(new byte[16]), 1));
    }
}
