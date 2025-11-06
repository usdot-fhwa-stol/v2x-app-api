package usdot.v2x.app.api.models.etx;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeoutErrorResponse {
    private String error;
    private String message;
    private String details;

    public TimeoutErrorResponse(String message) {
        this.error = "TIMEOUT_ERROR";
        this.message = message;
        this.details = "The request timed out while waiting for a response from the external service";
    }
}
