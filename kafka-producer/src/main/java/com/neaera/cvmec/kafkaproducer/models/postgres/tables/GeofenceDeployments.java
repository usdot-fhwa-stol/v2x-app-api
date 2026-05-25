package com.neaera.cvmec.kafkaproducer.models.postgres.tables;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@EqualsAndHashCode
@ToString
@Entity
@Table(name = "geofence_deployments")
public class GeofenceDeployments {
    @Id
    private Long id;

    @Column(name = "geofence_id")
    private String geofenceId;

    @Column(name = "geojson")
    private String geojson;

    @Column(name = "hex_payload")
    private String hexPayload;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deployed_by")
    private String deployedBy;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "msg_type")
    private String msgType;
}
