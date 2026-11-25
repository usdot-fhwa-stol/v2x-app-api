package usdot.v2x.app.api.crypto;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Thrown when a credential cannot be encrypted or decrypted because the
 * encryption key is missing or does not match the stored value. Mapped to
 * HTTP 503 by {@code GlobalExceptionHandler}'s {@link ResponseStatusException}
 * handler.
 */
public class CredentialEncryptionUnavailableException extends ResponseStatusException {

    public CredentialEncryptionUnavailableException(String reason) {
        super(HttpStatus.SERVICE_UNAVAILABLE, reason);
    }

    public CredentialEncryptionUnavailableException(String reason, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, reason, cause);
    }
}
