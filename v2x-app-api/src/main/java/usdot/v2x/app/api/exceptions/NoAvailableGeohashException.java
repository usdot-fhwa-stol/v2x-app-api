package usdot.v2x.app.api.exceptions;

/**
 * Exception thrown when no available geohash can be found for a geofence deployment.
 */
public class NoAvailableGeohashException extends RuntimeException {
    public NoAvailableGeohashException(String message) {
        super(message);
    }

    public NoAvailableGeohashException(String message, Throwable cause) {
        super(message, cause);
    }
}
