package usdot.v2x.app.api.config.crypto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for encrypting provider credentials at rest.
 * <p>
 * {@code encryptionKey} is a base64-encoded 256-bit AES key, typically supplied
 * through the {@code PROVIDER_CREDENTIALS_ENCRYPTION_KEY} environment variable
 * (generate one with {@code openssl rand -base64 32}). When the key is blank the
 * application still starts, but any attempt to encrypt or decrypt a credential
 * fails with HTTP 503.
 */
@Data
@Component
@ConfigurationProperties(prefix = "provider.credentials")
public class CryptoProperties {

    /** Base64-encoded 32-byte AES key. Blank disables credential encryption. */
    private String encryptionKey;

    /**
     * Version tag written in front of every ciphertext so values encrypted with
     * an older key can be identified during key rotation.
     */
    private int keyVersion = 1;
}
