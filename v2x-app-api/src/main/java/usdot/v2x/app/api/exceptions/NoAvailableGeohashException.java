package usdot.v2x.app.api.exceptions;

public class NoAvailableGeohashException extends RuntimeException {
    public NoAvailableGeohashException(String message) {
        super(message);
    }

    public NoAvailableGeohashException(String message, Throwable cause) {
        super(message, cause);
    }
}
