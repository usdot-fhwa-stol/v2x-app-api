package usdot.v2x.app.api.models.etx.registration;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;

import java.time.Instant;

@Entity
@Table(name = "registration_logs")
@Data
public class RegistrationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false)
    private RegistrationClientType clientType;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_subtype", nullable = false)
    private RegistrationClientSubType clientSubtype;

    @Column(name = "vendor_id", nullable = false)
    private String vendorId;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "registration_count", nullable = false)
    private Integer registrationCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "unregister_at")
    private Instant unregisterAt;

    @Column(name = "last_connected")
    private Instant lastConnected;
}
