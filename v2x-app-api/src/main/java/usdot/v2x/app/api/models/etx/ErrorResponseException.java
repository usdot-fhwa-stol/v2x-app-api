package usdot.v2x.app.api.models.etx;

import org.springframework.http.HttpStatusCode;

public class ErrorResponseException extends RuntimeException {
    private final HttpStatusCode statusCode;
    private final ErrorResponse errorResponse;

    public ErrorResponseException(ErrorResponse response, HttpStatusCode statusCode) {
        super(response.getError());
        this.errorResponse = response;
        this.statusCode = statusCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public ErrorResponse getErrorResponse() {
        return errorResponse;
    }
}
