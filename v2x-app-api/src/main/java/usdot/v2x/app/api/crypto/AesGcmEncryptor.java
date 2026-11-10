package usdot.v2x.app.api.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import usdot.v2x.app.api.config.crypto.CryptoProperties;

/**
 * Encrypts and decrypts short secrets with AES-256-GCM.
 * <p>
 * Output format: {@code v{keyVersion}:base64(iv || ciphertext || tag)}, using a
 * random 12-byte IV per value and a 128-bit authentication tag. The version
 * prefix lets values written under an older key be detected after rotation.
 */
@Slf4j
@Component
public class AesGcmEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String VERSION_PREFIX = "v";
    private static final char VERSION_SEPARATOR = ':';

    private final SecretKey key;
    private final int keyVersion;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmEncryptor(CryptoProperties properties) {
        this.keyVersion = properties.getKeyVersion();
        this.key = parseKey(properties.getEncryptionKey());
        if (key == null) {
            log.warn("provider.credentials.encryption-key is not set; provider credential "
                    + "storage is disabled and credential endpoints will return 503");
        }
    }

    /**
     * @return true when a valid encryption key is configured
     */
    public boolean isConfigured() {
        return key != null;
    }

    /**
     * @param plaintext value to encrypt; {@code null} is returned unchanged
     * @return versioned, base64-encoded ciphertext
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return VERSION_PREFIX + keyVersion + VERSION_SEPARATOR
                    + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new CredentialEncryptionUnavailableException("Failed to encrypt credential", e);
        }
    }

    /**
     * @param encrypted value produced by {@link #encrypt(String)}; {@code null} is
     *                  returned unchanged
     * @return the decrypted plaintext
     */
    public String decrypt(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        requireKey();

        int separator = encrypted.indexOf(VERSION_SEPARATOR);
        if (!encrypted.startsWith(VERSION_PREFIX) || separator < 0) {
            throw new CredentialEncryptionUnavailableException("Stored credential has an unrecognized format");
        }
        String version = encrypted.substring(VERSION_PREFIX.length(), separator);
        if (!String.valueOf(keyVersion).equals(version)) {
            throw new CredentialEncryptionUnavailableException(
                    "Stored credential was encrypted with key version " + version
                            + " but the configured key version is " + keyVersion);
        }

        try {
            byte[] payload = Base64.getDecoder().decode(encrypted.substring(separator + 1));
            if (payload.length <= IV_LENGTH_BYTES) {
                throw new CredentialEncryptionUnavailableException("Stored credential is truncated");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, payload, 0, IV_LENGTH_BYTES));
            byte[] plaintext = cipher.doFinal(payload, IV_LENGTH_BYTES, payload.length - IV_LENGTH_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new CredentialEncryptionUnavailableException(
                    "Failed to decrypt credential; check provider.credentials.encryption-key", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new CredentialEncryptionUnavailableException(
                    "Provider credential encryption key is not configured");
        }
    }

    private static SecretKey parseKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            return null;
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("provider.credentials.encryption-key must be base64 encoded", e);
        }
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("provider.credentials.encryption-key must decode to "
                    + KEY_LENGTH_BYTES + " bytes (AES-256); got " + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
