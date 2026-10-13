package usdot.v2x.app.api.models.etx.registration;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Active registrations for a vendor as reported by ETX, compared against the
 * local registration_logs table.
 */
public @Data class ActiveRegistrationsResponse {
    private String vendorId;
    private int etxCount;
    private int localActiveCount;
    private List<ActiveRegistrationView> items = new ArrayList<>();
    /** Device IDs registered in ETX that have no active local row. */
    private List<String> untrackedInEtx = new ArrayList<>();
    /** Device IDs active locally that ETX no longer reports. */
    private List<String> staleLocal = new ArrayList<>();
}
