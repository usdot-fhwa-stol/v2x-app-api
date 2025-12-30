package usdot.v2x.app.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import usdot.v2x.app.api.models.mqtt.MqttDeviceLocation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository for MQTT device location management.
 */
@Repository
public interface MqttDeviceLocationRepository extends JpaRepository<MqttDeviceLocation, Long> {

    /**
     * Find device location by MQTT client ID.
     */
    Optional<MqttDeviceLocation> findByMqttClientId(String mqttClientId);

    /**
     * Find all active devices in the given geohashes.
     */
    @Query("SELECT d.mqttClientId FROM MqttDeviceLocation d " +
           "WHERE d.geohash IN :geohashes AND d.isActive = true")
    Set<String> findMqttClientIdsByGeohashes(@Param("geohashes") Set<String> geohashes);

    /**
     * Find all active devices within a radius of a point.
     */
    @Query(value = "SELECT mqtt_client_id FROM mqtt_device_locations " +
           "WHERE is_active = true " +
           "AND ST_DWithin(" +
           "  ST_MakePoint(longitude, latitude)::geography, " +
           "  ST_MakePoint(:lon, :lat)::geography, " +
           "  :radiusMeters" +
           ")", nativeQuery = true)
    Set<String> findMqttClientIdsInRadius(
        @Param("lat") double latitude,
        @Param("lon") double longitude,
        @Param("radiusMeters") double radiusMeters
    );

    /**
     * Find all active devices.
     */
    List<MqttDeviceLocation> findByIsActiveTrue();

    /**
     * Clean up stale entries older than the specified time.
     */
    @Modifying
    @Query("UPDATE MqttDeviceLocation d SET d.isActive = false " +
           "WHERE d.lastUpdated < :cutoffTime AND d.isActive = true")
    int deactivateStaleEntries(@Param("cutoffTime") Instant cutoffTime);

    /**
     * Update last connected timestamp.
     */
    @Modifying
    @Query("UPDATE MqttDeviceLocation d SET d.lastConnected = :timestamp " +
           "WHERE d.mqttClientId = :mqttClientId")
    int updateLastConnected(@Param("mqttClientId") String mqttClientId, @Param("timestamp") Instant timestamp);
}



