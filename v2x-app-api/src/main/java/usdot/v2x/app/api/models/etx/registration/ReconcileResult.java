package usdot.v2x.app.api.models.etx.registration;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of reconciling registration_logs against the ETX active client list.
 */
public @Data class ReconcileResult {
    private String vendorId;
    private int etxCount;
    /** Local rows marked inactive because ETX no longer reports them. */
    private List<String> deactivated = new ArrayList<>();
    /** Devices registered in ETX that have no active local row. */
    private List<String> untrackedInEtx = new ArrayList<>();
}
