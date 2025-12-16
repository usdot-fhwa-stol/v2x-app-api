package usdot.v2x.app.api.repositories;

import usdot.v2x.app.api.models.geofence.GeofenceDeployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for GeofenceDeployment entity operations.
 */
@Repository
public interface GeofenceDeploymentRepository extends JpaRepository<GeofenceDeployment, Long> {

    Optional<GeofenceDeployment> findByGeofenceId(String geofenceId);

    List<GeofenceDeployment> findByDeployedByAndIsActive(String deployedBy, Boolean isActive);

    List<GeofenceDeployment> findByIsActive(Boolean isActive);

    @Query("SELECT gd FROM GeofenceDeployment gd " +
            "JOIN gd.geohashes gg " +
            "WHERE gg.geohash = :geohash")
    List<GeofenceDeployment> findByGeohash(@Param("geohash") String geohash);

    @Query("SELECT gd FROM GeofenceDeployment gd " +
            "JOIN gd.geohashes gg " +
            "WHERE gg.geohash = :geohash AND gd.isActive = :isActive")
    List<GeofenceDeployment> findByGeohashAndIsActive(@Param("geohash") String geohash,
            @Param("isActive") Boolean isActive);

    /**
     * Find all active Geofence deployments that have expired (including grace
     * period)
     * 
     * @param currentTime The current time to compare against
     * @return List of expired active Geofence deployments
     */
    @Query("SELECT gd FROM GeofenceDeployment gd " +
            "WHERE gd.isActive = true " +
            "AND gd.expiresAt IS NOT NULL " +
            "AND gd.expiresAt < :currentTime")
    List<GeofenceDeployment> findExpiredActiveGeofenceDeployments(@Param("currentTime") java.time.Instant currentTime);

    /**
     * Deactivate all expired Geofence deployments in a single query (including
     * grace
     * period)
     * 
     * @param currentTime The current time to compare against
     * @return Number of Geofence deployments deactivated
     */
    @Modifying
    @Query("UPDATE GeofenceDeployment gd " +
            "SET gd.isActive = false, gd.updatedAt = :currentTime " +
            "WHERE gd.isActive = true " +
            "AND gd.expiresAt IS NOT NULL " +
            "AND gd.expiresAt < :currentTime")
    int deactivateExpiredGeofenceDeployments(@Param("currentTime") java.time.Instant currentTime);

    boolean existsByGeofenceId(String geofenceId);
}
