package usdot.v2x.app.api.repositories;

import usdot.v2x.app.api.models.etx.registration.RegistrationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RegistrationLogRepository extends JpaRepository<RegistrationLog, Long> {

    /**
     * Find all active registrations for a specific user
     */
    @Query("SELECT r FROM RegistrationLog r WHERE r.requestedBy = :requestedBy AND r.isActive = true ORDER BY r.createdAt DESC")
    List<RegistrationLog> findActiveRegistrationsByUser(@Param("requestedBy") String requestedBy);

    /**
     * Count active registrations for a specific user
     */
    @Query("SELECT COUNT(r) FROM RegistrationLog r WHERE r.requestedBy = :requestedBy AND r.isActive = true")
    Long countActiveRegistrationsByUser(@Param("requestedBy") String requestedBy);

    /**
     * Find registrations that are expired
     */
    @Query("SELECT r FROM RegistrationLog r WHERE r.expiresAt < :currentTime AND r.isActive = true")
    List<RegistrationLog> findExpiredRegistrations(@Param("currentTime") Instant currentTime);

    /**
     * Find oldest registrations for cleanup when count exceeds limit
     * Uses lastConnected timestamp, falling back to createdAt if lastConnected is
     * null
     */
    @Query("SELECT r FROM RegistrationLog r WHERE r.requestedBy = :requestedBy AND r.isActive = true ORDER BY COALESCE(r.lastConnected, r.createdAt) ASC")
    List<RegistrationLog> findOldestRegistrationsByUser(@Param("requestedBy") String requestedBy);

    /**
     * Find registrations by device IDs
     */
    @Query("SELECT r FROM RegistrationLog r WHERE r.deviceId IN :deviceIds AND r.isActive = true")
    List<RegistrationLog> findByDeviceIds(@Param("deviceIds") List<String> deviceIds);

    /**
     * Count total active registrations by vendor ID (all types combined)
     */
    @Query("SELECT COUNT(r) FROM RegistrationLog r WHERE r.vendorId = :vendorId AND r.isActive = true")
    Long countTotalActiveRegistrationsByVendor(@Param("vendorId") String vendorId);

    /**
     * Find oldest registrations by vendor ID for cleanup (all types)
     * Uses lastConnected timestamp, falling back to createdAt if lastConnected is
     * null
     */
    @Query("SELECT r FROM RegistrationLog r WHERE r.vendorId = :vendorId AND r.isActive = true ORDER BY COALESCE(r.lastConnected, r.createdAt) ASC")
    List<RegistrationLog> findOldestTotalRegistrationsByVendor(@Param("vendorId") String vendorId);
}
