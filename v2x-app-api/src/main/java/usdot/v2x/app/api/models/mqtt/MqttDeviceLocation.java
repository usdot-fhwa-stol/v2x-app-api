package usdot.v2x.app.api.models.mqtt;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Entity for storing MQTT device locations for geo-routing.
 * Tracks MQTT client IDs and their geographic locations.
 */
@Entity
@Table(name = "mqtt_device_locations", indexes = {
    @Index(name = "idx_mqtt_client_id", columnList = "mqtt_client_id"),
    @Index(name = "idx_geohash", columnList = "geohash"),
    @Index(name = "idx_last_updated", columnList = "last_updated")
})
@Data
@EntityListeners(AuditingEntityListener.class)
public class MqttDeviceLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mqtt_client_id", nullable = false, unique = true, length = 255)
    private String mqttClientId;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "geohash", length = 20)
    private String geohash;

    @Column(name = "elevation")
    private Double elevation;

    @Column(name = "heading")
    private Double heading;

    @Column(name = "speed")
    private Double speed;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    @Column(name = "last_connected")
    private Instant lastConnected;

    @Column(name = "vendor_id")
    private String vendorId;

    @Column(name = "registered_by")
    private String registeredBy;
}



