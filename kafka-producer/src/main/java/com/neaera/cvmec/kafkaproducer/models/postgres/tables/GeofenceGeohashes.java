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
@Table(name = "geofence_geohashes")
public class GeofenceGeohashes {
    @Id
    private Long id;

    @Column(name = "geofence_deployment_id")
    private Long geofenceDeploymentId;

    @Column(name = "geohash")
    private String geohash;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
