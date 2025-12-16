package usdot.v2x.app.api.repositories;

import usdot.v2x.app.api.models.geofence.GeofenceGeohash;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for GeofenceGeohash entity operations.
 */
@Repository
public interface GeofenceGeohashRepository extends JpaRepository<GeofenceGeohash, Long> {

    List<GeofenceGeohash> findByGeohash(String geohash);

    @Query("SELECT gg FROM GeofenceGeohash gg " +
            "JOIN gg.geofenceDeployment gd " +
            "WHERE gd.geofenceId = :geofenceId")
    List<GeofenceGeohash> findByGeofenceId(@Param("geofenceId") String geofenceId);

    @Query("SELECT gg FROM GeofenceGeohash gg " +
            "JOIN gg.geofenceDeployment gd " +
            "WHERE gd.geofenceId = :geofenceId AND gd.isActive = :isActive")
    List<GeofenceGeohash> findByGeofenceIdAndIsActive(@Param("geofenceId") String geofenceId,
            @Param("isActive") Boolean isActive);

    void deleteByGeofenceDeploymentId(Long geofenceDeploymentId);
}
