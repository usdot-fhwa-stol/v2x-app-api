package usdot.v2x.app.api.repositories;

import usdot.v2x.app.api.models.VendorLimits;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorLimitsRepository extends JpaRepository<VendorLimits, Long> {

    /**
     * Find vendor limits by vendor ID (active only)
     */
    @Query("SELECT v FROM VendorLimits v WHERE v.vendorId = :vendorId AND v.isActive = true")
    Optional<VendorLimits> findByVendorId(@Param("vendorId") String vendorId);

    /**
     * Find vendor limits by vendor ID (regardless of active status)
     * Used for re-activation scenarios
     */
    @Query("SELECT v FROM VendorLimits v WHERE v.vendorId = :vendorId")
    Optional<VendorLimits> findByVendorIdIgnoreActive(@Param("vendorId") String vendorId);

    /**
     * Find all active vendor limits
     */
    @Query("SELECT v FROM VendorLimits v WHERE v.isActive = true ORDER BY v.vendorId")
    List<VendorLimits> findAllActive();

    /**
     * Check if vendor limits exist for vendor ID
     */
    @Query("SELECT COUNT(v) > 0 FROM VendorLimits v WHERE v.vendorId = :vendorId AND v.isActive = true")
    boolean existsByVendorId(@Param("vendorId") String vendorId);
}
