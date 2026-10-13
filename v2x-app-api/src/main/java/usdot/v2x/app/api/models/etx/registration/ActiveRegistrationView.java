package usdot.v2x.app.api.models.etx.registration;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * An active ETX registration merged with the local tracking data from
 * registration_logs, when a local row exists.
 */
public @Data class ActiveRegistrationView {
    // Reported by ETX
    private String deviceId;
    private RegistrationClientType clientType;
    private RegistrationClientSubType clientSubtype;
    private List<String> mecIds;

    // Tracked locally (null when trackedLocally is false)
    private boolean trackedLocally;
    private String requestedBy;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant lastConnected;
}
