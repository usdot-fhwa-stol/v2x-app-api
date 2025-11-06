package usdot.v2x.app.api.repositories;

import usdot.v2x.app.api.models.UserLimits;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserLimitsRepository extends JpaRepository<UserLimits, Long> {

    /**
     * Find user limits by username and vendor ID
     */
    @Query("SELECT u FROM UserLimits u WHERE u.username = :username AND u.vendorId = :vendorId AND u.isActive = true")
    Optional<UserLimits> findByUsernameAndVendorId(@Param("username") String username,
            @Param("vendorId") String vendorId);

    /**
     * Find user limits by username (for any vendor)
     */
    @Query("SELECT u FROM UserLimits u WHERE u.username = :username AND u.isActive = true")
    List<UserLimits> findByUsername(@Param("username") String username);

    /**
     * Find all active user limits
     */
    @Query("SELECT u FROM UserLimits u WHERE u.isActive = true ORDER BY u.username, u.vendorId")
    List<UserLimits> findAllActive();

    /**
     * Find user limits by vendor ID
     */
    @Query("SELECT u FROM UserLimits u WHERE u.vendorId = :vendorId AND u.isActive = true")
    List<UserLimits> findByVendorId(@Param("vendorId") String vendorId);

    /**
     * Check if user limits exist for username and vendor
     */
    @Query("SELECT COUNT(u) > 0 FROM UserLimits u WHERE u.username = :username AND u.vendorId = :vendorId AND u.isActive = true")
    boolean existsByUsernameAndVendorId(@Param("username") String username, @Param("vendorId") String vendorId);

    /**
     * Find inactive user limits by vendor ID (for re-activation)
     */
    @Query("SELECT u FROM UserLimits u WHERE u.vendorId = :vendorId AND u.isActive = false")
    List<UserLimits> findInactiveByVendorId(@Param("vendorId") String vendorId);
}
