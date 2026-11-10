package usdot.v2x.app.api.exceptions;

import org.springframework.http.HttpStatus;

import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.etx.ErrorResponseException;

/**
 * Raised when geohash coverage would exceed a configured deployment-region
 * resource limit.
 */
public class GeofenceTooLargeException extends ErrorResponseException {

    public GeofenceTooLargeException(String message) {
        super(new ErrorResponse("GEOFENCE_TOO_LARGE", message), HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
