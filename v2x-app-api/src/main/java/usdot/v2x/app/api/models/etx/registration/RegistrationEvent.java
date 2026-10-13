package usdot.v2x.app.api.models.etx.registration;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * Append-only record of a registration lifecycle action, used to correlate
 * registration/eviction activity with message loss.
 */
@Entity
@Table(name = "registration_events", indexes = {
        @Index(name = "idx_registration_events_device_created", columnList = "device_id, created_at"),
        @Index(name = "idx_registration_events_vendor_created", columnList = "vendor_id, created_at"),
        @Index(name = "idx_registration_events_type", columnList = "event_type")
})
@Data
public class RegistrationEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "vendor_id")
    private String vendorId;

    @Column(name = "requested_by")
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private RegistrationEventType eventType;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "etx_status")
    private Integer etxStatus;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
