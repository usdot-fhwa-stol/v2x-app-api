package usdot.v2x.app.api.models.geofence;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;

import java.time.Instant;

@Entity
@Table(name = "geofence_geohashes")
@Data
public class GeofenceGeohash {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geofence_deployment_id", nullable = false)
    private GeofenceDeployment geofenceDeployment;

    @Column(name = "geohash", nullable = false)
    private String geohash;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
