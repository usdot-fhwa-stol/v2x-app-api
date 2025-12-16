package usdot.v2x.app.api.models.geofence;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "geofence_deployments")
@Data
@EntityListeners(AuditingEntityListener.class)
public class GeofenceDeployment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "geofence_id", nullable = false, unique = true)
    private String geofenceId;

    @Column(name = "geojson", nullable = false, columnDefinition = "JSONB")
    private String geojson;

    @Column(name = "msg_type", nullable = false)
    private String msgType;

    @Column(name = "hex_payload", nullable = false, columnDefinition = "TEXT")
    private String hexPayload;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deployed_by", nullable = false)
    private String deployedBy;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @OneToMany(mappedBy = "geofenceDeployment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GeofenceGeohash> geohashes;
}
