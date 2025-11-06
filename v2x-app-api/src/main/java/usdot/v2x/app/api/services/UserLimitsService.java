package usdot.v2x.app.api.services;

import usdot.v2x.app.api.models.UserLimits;
import usdot.v2x.app.api.repositories.UserLimitsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class UserLimitsService {

    @Autowired
    private UserLimitsRepository userLimitsRepository;

    /**
     * Get user limits for a specific user and vendor
     * Returns default limits if no custom limits are set
     */
    public UserLimits getUserLimits(String username, String vendorId, int defaultUserLimit) {
        Optional<UserLimits> userLimits = userLimitsRepository.findByUsernameAndVendorId(username, vendorId);

        if (userLimits.isPresent()) {
            return userLimits.get();
        }

        // Return default limits if no custom limits found
        UserLimits defaultLimits = new UserLimits();
        defaultLimits.setUsername(username);
        defaultLimits.setVendorId(vendorId);
        defaultLimits.setMaxRegistrations(defaultUserLimit);
        defaultLimits.setIsActive(true);
        defaultLimits.setCreatedAt(Instant.now());
        defaultLimits.setUpdatedAt(Instant.now());

        return defaultLimits;
    }

    /**
     * Create or update user limits
     */
    @Transactional
    public UserLimits createOrUpdateUserLimits(String username, String vendorId,
            Integer maxRegistrations, String updatedBy) {
        Optional<UserLimits> existingLimits = userLimitsRepository.findByUsernameAndVendorId(username, vendorId);

        if (existingLimits.isPresent()) {
            // Update existing limits
            UserLimits limits = existingLimits.get();
            limits.setMaxRegistrations(maxRegistrations);
            limits.setUpdatedBy(updatedBy);
            limits.setUpdatedAt(Instant.now());

            UserLimits saved = userLimitsRepository.save(limits);
            log.info("Updated user limits for user: {}, vendor: {}, maxRegistrations: {}",
                    username, vendorId, maxRegistrations);
            return saved;
        } else {
            // Create new limits
            UserLimits limits = new UserLimits();
            limits.setUsername(username);
            limits.setVendorId(vendorId);
            limits.setMaxRegistrations(maxRegistrations);
            limits.setIsActive(true);
            limits.setCreatedBy(updatedBy);
            limits.setUpdatedBy(updatedBy);
            limits.setCreatedAt(Instant.now());
            limits.setUpdatedAt(Instant.now());

            UserLimits saved = userLimitsRepository.save(limits);
            log.info("Created user limits for user: {}, vendor: {}, maxRegistrations: {}",
                    username, vendorId, maxRegistrations);
            return saved;
        }
    }

    /**
     * Get all user limits
     */
    public List<UserLimits> getAllUserLimits() {
        return userLimitsRepository.findAllActive();
    }

    /**
     * Get user limits by username
     */
    public List<UserLimits> getUserLimitsByUsername(String username) {
        return userLimitsRepository.findByUsername(username);
    }

    /**
     * Get user limits by vendor ID
     */
    public List<UserLimits> getUserLimitsByVendorId(String vendorId) {
        return userLimitsRepository.findByVendorId(vendorId);
    }

    /**
     * Delete user limits (soft delete)
     */
    @Transactional
    public void deleteUserLimits(String username, String vendorId, String deletedBy) {
        Optional<UserLimits> limits = userLimitsRepository.findByUsernameAndVendorId(username, vendorId);
        if (limits.isPresent()) {
            UserLimits userLimits = limits.get();
            userLimits.setIsActive(false);
            userLimits.setUpdatedBy(deletedBy);
            userLimits.setUpdatedAt(Instant.now());
            userLimitsRepository.save(userLimits);
            log.info("Soft deleted user limits for user: {}, vendor: {}", username, vendorId);
        }
    }

    /**
     * Delete all user limits for a specific vendor (hard delete)
     * This is called when a vendor is deleted
     */
    @Transactional
    public void deleteAllUserLimitsForVendor(String vendorId, String deletedBy) {
        List<UserLimits> userLimits = userLimitsRepository.findByVendorId(vendorId);
        if (!userLimits.isEmpty()) {
            userLimitsRepository.deleteAll(userLimits);
            log.info("Hard deleted {} user limits for vendor: {}", userLimits.size(), vendorId);
        }
    }

    /**
     * Soft delete all user limits for a specific vendor
     * This is called when a vendor is soft deleted
     */
    @Transactional
    public void softDeleteAllUserLimitsForVendor(String vendorId, String deletedBy) {
        List<UserLimits> userLimits = userLimitsRepository.findByVendorId(vendorId);
        if (!userLimits.isEmpty()) {
            userLimits.forEach(userLimit -> {
                userLimit.setIsActive(false);
                userLimit.setUpdatedBy(deletedBy);
                userLimit.setUpdatedAt(Instant.now());
            });
            userLimitsRepository.saveAll(userLimits);
            log.info("Soft deleted {} user limits for vendor: {}", userLimits.size(), vendorId);
        }
    }

    /**
     * Check if user has custom limits
     */
    public boolean hasCustomLimits(String username, String vendorId) {
        return userLimitsRepository.existsByUsernameAndVendorId(username, vendorId);
    }

    /**
     * Re-activate all inactive user limits for a specific vendor
     * This is called when a vendor is re-added
     */
    @Transactional
    public void reactivateUserLimitsForVendor(String vendorId, String reactivatedBy) {
        List<UserLimits> inactiveUserLimits = userLimitsRepository.findInactiveByVendorId(vendorId);
        if (!inactiveUserLimits.isEmpty()) {
            inactiveUserLimits.forEach(userLimit -> {
                userLimit.setIsActive(true);
                userLimit.setUpdatedBy(reactivatedBy);
                userLimit.setUpdatedAt(Instant.now());
            });
            userLimitsRepository.saveAll(inactiveUserLimits);
            log.info("Re-activated {} user limits for vendor: {}", inactiveUserLimits.size(), vendorId);
        }
    }
}
