package usdot.v2x.app.api.services;

import usdot.v2x.app.api.models.VendorLimits;
import usdot.v2x.app.api.repositories.VendorLimitsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class VendorLimitsService {

    @Autowired
    private VendorLimitsRepository vendorLimitsRepository;

    @Autowired
    private UserLimitsService userLimitsService;

    /**
     * Get vendor limits for a specific vendor
     * Returns default limits if no custom limits are set
     */
    public VendorLimits getVendorLimits(String vendorId, int defaultLimit) {
        Optional<VendorLimits> vendorLimits = vendorLimitsRepository.findByVendorId(vendorId);

        if (vendorLimits.isPresent()) {
            return vendorLimits.get();
        }

        // Return default limits if no custom limits found
        VendorLimits defaultLimits = new VendorLimits();
        defaultLimits.setVendorId(vendorId);
        defaultLimits.setMaxRegistrations(defaultLimit);
        defaultLimits.setIsActive(true);
        defaultLimits.setCreatedAt(Instant.now());
        defaultLimits.setUpdatedAt(Instant.now());

        return defaultLimits;
    }

    /**
     * Create or update vendor limits
     */
    @Transactional
    public VendorLimits createOrUpdateVendorLimits(String vendorId, Integer maxRegistrations, String updatedBy) {
        Optional<VendorLimits> existingLimits = vendorLimitsRepository.findByVendorIdIgnoreActive(vendorId);

        if (existingLimits.isPresent()) {
            // Update existing limits (including re-activation if soft-deleted)
            VendorLimits limits = existingLimits.get();
            limits.setMaxRegistrations(maxRegistrations);
            limits.setIsActive(true); // Re-activate if it was soft-deleted
            limits.setUpdatedBy(updatedBy);
            limits.setUpdatedAt(Instant.now());

            VendorLimits saved = vendorLimitsRepository.save(limits);

            // Re-activate any previously deactivated user limits for this vendor
            userLimitsService.reactivateUserLimitsForVendor(vendorId, updatedBy);

            log.info("Updated/re-activated vendor limits for vendor: {}, maxRegistrations: {}", vendorId,
                    maxRegistrations);
            return saved;
        } else {
            // Create new limits
            VendorLimits limits = new VendorLimits();
            limits.setVendorId(vendorId);
            limits.setMaxRegistrations(maxRegistrations);
            limits.setIsActive(true);
            limits.setCreatedBy(updatedBy);
            limits.setUpdatedBy(updatedBy);
            limits.setCreatedAt(Instant.now());
            limits.setUpdatedAt(Instant.now());

            VendorLimits saved = vendorLimitsRepository.save(limits);

            // Re-activate any previously deactivated user limits for this vendor
            userLimitsService.reactivateUserLimitsForVendor(vendorId, updatedBy);

            log.info("Created vendor limits for vendor: {}, maxRegistrations: {}", vendorId, maxRegistrations);
            return saved;
        }
    }

    /**
     * Get all vendor limits
     */
    public List<VendorLimits> getAllVendorLimits() {
        return vendorLimitsRepository.findAllActive();
    }

    /**
     * Delete vendor limits (soft delete)
     * This will also soft delete all related user limits to maintain data integrity
     */
    @Transactional
    public void deleteVendorLimits(String vendorId, String deletedBy) {
        Optional<VendorLimits> limits = vendorLimitsRepository.findByVendorIdIgnoreActive(vendorId);
        if (limits.isPresent()) {
            VendorLimits vendorLimits = limits.get();
            vendorLimits.setIsActive(false);
            vendorLimits.setUpdatedBy(deletedBy);
            vendorLimits.setUpdatedAt(Instant.now());
            vendorLimitsRepository.save(vendorLimits);

            // Soft delete all related user limits for this vendor
            userLimitsService.softDeleteAllUserLimitsForVendor(vendorId, deletedBy);

            log.info("Soft deleted vendor limits for vendor: {} (and all related user limits)", vendorId);
        }
    }

    /**
     * Check if vendor has custom limits
     */
    public boolean hasCustomLimits(String vendorId) {
        return vendorLimitsRepository.existsByVendorId(vendorId);
    }
}
