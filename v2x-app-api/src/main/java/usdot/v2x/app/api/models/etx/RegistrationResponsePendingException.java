package usdot.v2x.app.api.models.etx;

import usdot.v2x.app.api.models.etx.registration.RegistrationPendingResponse;

import org.springframework.http.HttpStatusCode;

public class RegistrationResponsePendingException extends RuntimeException {
    private final HttpStatusCode statusCode;

    public RegistrationResponsePendingException(RegistrationPendingResponse response, HttpStatusCode statusCode) {
        super("Registration is pending: " + response);
        this.statusCode = statusCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}
