package com.neaera.cvmec.kafkaproducer.repositories;

import com.neaera.cvmec.kafkaproducer.models.postgres.derived.GeohashPayloadMessage;
import com.neaera.cvmec.kafkaproducer.models.postgres.tables.GeofenceGeohashes;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for GeofenceGeohashes entity operations.
 */
@Repository
public interface GeofenceGeohashesRepository extends JpaRepository<GeofenceGeohashes, Long> {

    @Query("SELECT new com.neaera.cvmec.kafkaproducer.models.postgres.derived.GeohashPayloadMessage("
            + "gg.geohash, gd.hexPayload) "
            + "FROM GeofenceGeohashes gg "
            + "JOIN GeofenceDeployments gd ON gg.geofenceDeploymentId = gd.id "
            + "WHERE gd.isActive = true")
    List<GeohashPayloadMessage> findActiveGeohashPayloads();
}
