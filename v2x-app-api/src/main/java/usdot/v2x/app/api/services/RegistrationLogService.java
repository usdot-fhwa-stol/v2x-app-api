package usdot.v2x.app.api.services;

import usdot.v2x.app.api.config.etx.EtxProperties;
import usdot.v2x.app.api.etx.registration.RegistrationApi;
import usdot.v2x.app.api.models.UserLimits;
import usdot.v2x.app.api.models.VendorLimits;
import usdot.v2x.app.api.models.etx.registration.RegistrationClientSubType;
import usdot.v2x.app.api.models.etx.registration.RegistrationClientType;
import usdot.v2x.app.api.models.etx.registration.RegistrationLog;
import usdot.v2x.app.api.models.etx.registration.RegistrationResponse;
import usdot.v2x.app.api.repositories.RegistrationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing ETX registration logs and operations.
 */
@Slf4j
@Service
public class RegistrationLogService {

    @Autowired
    private RegistrationLogRepository registrationLogRepository;

    @Autowired
    private RegistrationApi registrationApi;

    @Autowired
    private EtxProperties etxProperties;

    @Autowired
    private UserLimitsService userLimitsService;

    @Autowired
    private VendorLimitsService vendorLimitsService;

    /**
     * Log a successful registration
     */
    @Transactional
    public void logRegistration(RegistrationResponse registrationResponse, RegistrationClientType clientType,
            RegistrationClientSubType clientSubtype,
            String vendorId,
            String requestedBy) {
        try {
            // Get default limits from configuration as fallback
            int defaultUserLimit = getDefaultUserLimitFromConfig(vendorId);
            int defaultVendorLimit = getDefaultVendorLimitFromConfig(vendorId);

            // Check user limits first - get actual user limit from database
            UserLimits userLimits = userLimitsService.getUserLimits(requestedBy, vendorId, defaultUserLimit);
            Long userCount = registrationLogRepository.countActiveRegistrationsByUser(requestedBy);
            int userLimit = userLimits.getMaxRegistrations();
            if (userCount >= userLimit) {
                log.warn("User {} has reached maximum registrations ({}), cleaning up oldest", requestedBy, userLimit);
                cleanupOldRegistrations(requestedBy);
                userCount = registrationLogRepository.countActiveRegistrationsByUser(requestedBy);
            }

            // Check vendor limits - get actual vendor limit from database
            VendorLimits vendorLimits = vendorLimitsService.getVendorLimits(vendorId, defaultVendorLimit);
            Long vendorCount = registrationLogRepository.countTotalActiveRegistrationsByVendor(vendorId);
            int vendorLimit = vendorLimits.getMaxRegistrations();

            if (vendorCount >= vendorLimit) {
                log.warn("Vendor {} has reached registration limit ({}), cleaning up oldest", vendorId, vendorLimit);
                cleanupOldRegistrationsByVendor(vendorId);
            }

            // Create and save registration log
            RegistrationLog logEntry = new RegistrationLog();
            logEntry.setDeviceId(registrationResponse.getDeviceId());
            logEntry.setClientType(clientType);
            logEntry.setClientSubtype(clientSubtype);
            logEntry.setVendorId(vendorId);
            logEntry.setRequestedBy(requestedBy);
            logEntry.setRegistrationCount(userCount.intValue() + 1);
            logEntry.setCreatedAt(Instant.now());
            logEntry.setExpiresAt(parseExpirationTime(registrationResponse.getCertificate().getExpirationTime()));
            logEntry.setIsActive(true);
            // Set initial lastConnected timestamp to creation time
            logEntry.setLastConnected(Instant.now());

            registrationLogRepository.save(logEntry);

            log.info(
                    "Logged registration for user: {}, device: {}, type: {}, vendor: {}, userCount: {}, vendorCount: {}",
                    requestedBy, registrationResponse.getDeviceId(), clientType, vendorId, userCount + 1,
                    vendorCount + 1);

        } catch (Exception e) {
            log.error("Failed to log registration: {}", e.getMessage(), e);
        }
    }

    /**
     * Clean up old registrations when count exceeds limit
     */
    @Transactional
    public List<String> cleanupOldRegistrations(String requestedBy) {
        try {
            List<RegistrationLog> oldestRegistrations = registrationLogRepository
                    .findOldestRegistrationsByUser(requestedBy);

            // Get the user's vendor to determine the correct user limit
            String vendorId = oldestRegistrations.isEmpty() ? null : oldestRegistrations.get(0).getVendorId();

            // Get actual user limit from database
            int defaultUserLimit = getDefaultUserLimitFromConfig(vendorId);
            UserLimits userLimits = userLimitsService.getUserLimits(requestedBy, vendorId, defaultUserLimit);
            int userLimit = userLimits.getMaxRegistrations();

            // Remove oldest registrations to keep count under limit
            int registrationsToRemove = oldestRegistrations.size() - userLimit + 1;
            if (registrationsToRemove > 0) {
                List<RegistrationLog> toRemove = oldestRegistrations.subList(0, registrationsToRemove);
                List<String> deviceIds = toRemove.stream()
                        .map(RegistrationLog::getDeviceId)
                        .collect(Collectors.toList());

                // Call ETX API to delete registrations first
                if (!deviceIds.isEmpty()) {
                    try {
                        // Get vendorId from the first registration to delete
                        String registrationVendorId = toRemove.isEmpty() ? null : toRemove.get(0).getVendorId();

                        // Make the ETX API call synchronous
                        registrationApi.deleteRegistrations(deviceIds, registrationVendorId).block();

                        // Only mark as inactive if successfully deleted from ETX
                        toRemove.forEach(log -> {
                            log.setIsActive(false);
                            log.setUnregisterAt(Instant.now());
                        });
                        registrationLogRepository.saveAll(toRemove);
                        log.info("Successfully deleted {} user registrations from ETX and marked as inactive",
                                deviceIds.size());
                        return deviceIds;
                    } catch (Exception e) {
                        log.error("Failed to delete user registrations from ETX: {}", e.getMessage());
                        // Return empty list since ETX deletion failed
                        return new ArrayList<>();
                    }
                } else {
                    // If no device IDs, just mark as inactive
                    toRemove.forEach(log -> {
                        log.setIsActive(false);
                        log.setUnregisterAt(Instant.now());
                    });
                    registrationLogRepository.saveAll(toRemove);
                    log.info("Marked {} old registrations as inactive for user: {}", registrationsToRemove,
                            requestedBy);
                    return new ArrayList<>();
                }
            }
            return new ArrayList<>();

        } catch (Exception e) {
            log.error("Failed to cleanup old registrations for user {}: {}", requestedBy, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Get active registrations for a user
     */
    public List<RegistrationLog> getActiveRegistrations(String requestedBy) {
        return registrationLogRepository.findActiveRegistrationsByUser(requestedBy);
    }

    /**
     * Get registration count for a user
     */
    public Long getRegistrationCount(String requestedBy) {
        return registrationLogRepository.countActiveRegistrationsByUser(requestedBy);
    }

    /**
     * Get registration count for a vendor
     */
    public Long getVendorRegistrationCount(String vendorId) {
        return registrationLogRepository.countTotalActiveRegistrationsByVendor(vendorId);
    }

    /**
     * Update the last connected timestamp for a device
     */
    @Transactional
    public void updateLastConnected(String deviceId) {
        try {
            List<RegistrationLog> logs = registrationLogRepository.findByDeviceIds(List.of(deviceId));
            if (!logs.isEmpty()) {
                Instant now = Instant.now();
                logs.forEach(log -> {
                    log.setLastConnected(now);
                });
                registrationLogRepository.saveAll(logs);
                log.debug("Updated last connected timestamp for device: {}", deviceId);
            } else {
                log.warn("No active registration found for device: {}", deviceId);
            }
        } catch (Exception e) {
            log.error("Failed to update last connected timestamp for device {}: {}", deviceId, e.getMessage(), e);
        }
    }

    /**
     * Check capacity and clean up if needed before registration
     * This method ensures there's capacity available for new registrations
     * User limits and vendor limits are checked independently:
     * - User limits: How many registrations this specific user can have
     * - Vendor limits: How many total registrations this vendor can have across all
     * users
     */
    @Transactional
    public void ensureCapacityForRegistration(String vendorId, String requestedBy) {
        try {
            // Get default limits from configuration as fallback
            int defaultUserLimit = getDefaultUserLimitFromConfig(vendorId);
            int defaultVendorLimit = getDefaultVendorLimitFromConfig(vendorId);

            // Check USER limits (how many registrations this specific user can have)
            // This gets the actual limit from the database, using config as fallback
            UserLimits userLimits = userLimitsService.getUserLimits(requestedBy, vendorId, defaultUserLimit);
            Long userCount = getRegistrationCount(requestedBy);
            int userLimit = userLimits.getMaxRegistrations();
            if (userCount >= userLimit) {
                log.info("User {} has reached their personal limit ({}), cleaning up their oldest registrations",
                        requestedBy, userLimit);
                cleanupOldRegistrations(requestedBy);
            }

            // Check VENDOR limits (how many total registrations this vendor can have across
            // all users)
            // This gets the actual limit from the database, using config as fallback
            VendorLimits vendorLimits = vendorLimitsService.getVendorLimits(vendorId, defaultVendorLimit);
            Long vendorCount = getVendorRegistrationCount(vendorId);
            int vendorLimit = vendorLimits.getMaxRegistrations();
            if (vendorCount >= vendorLimit) {
                log.info("Vendor {} has reached total limit ({}), cleaning up oldest registrations across all users",
                        vendorId, vendorLimit);
                cleanupOldRegistrationsByVendor(vendorId);
            }
        } catch (Exception e) {
            log.warn("Failed to check/cleanup capacity before registration: {}", e.getMessage());
            // Continue with registration even if cleanup fails
        }
    }

    /**
     * Clean up expired registrations
     */
    @Transactional
    public List<String> cleanupExpiredRegistrations() {
        try {
            List<RegistrationLog> expiredRegistrations = registrationLogRepository
                    .findExpiredRegistrations(Instant.now());

            if (!expiredRegistrations.isEmpty()) {
                List<String> deviceIds = expiredRegistrations.stream()
                        .map(RegistrationLog::getDeviceId)
                        .collect(Collectors.toList());

                // Mark as inactive and set unregister timestamp
                expiredRegistrations.forEach(log -> {
                    log.setIsActive(false);
                    log.setUnregisterAt(Instant.now());
                });
                registrationLogRepository.saveAll(expiredRegistrations);

                log.info("Marked {} expired registrations as inactive", expiredRegistrations.size());

                // Call ETX API to delete registrations
                if (!deviceIds.isEmpty()) {
                    try {
                        // Group registrations by vendorId and delete each group separately
                        expiredRegistrations.stream()
                                .collect(Collectors.groupingBy(RegistrationLog::getVendorId))
                                .forEach((vendorId, vendorRegistrations) -> {
                                    List<String> vendorDeviceIds = vendorRegistrations.stream()
                                            .map(RegistrationLog::getDeviceId)
                                            .collect(Collectors.toList());

                                    try {
                                        registrationApi.deleteRegistrations(vendorDeviceIds, vendorId).block();
                                        log.info("Successfully deleted {} expired registrations from ETX for vendor {}",
                                                vendorDeviceIds.size(), vendorId);
                                    } catch (Exception e) {
                                        log.error("Failed to delete expired registrations from ETX for vendor {}: {}",
                                                vendorId, e.getMessage());
                                    }
                                });
                    } catch (Exception e) {
                        log.error("Error calling ETX API to delete expired registrations: {}", e.getMessage(), e);
                    }
                }
                return deviceIds;
            }
            return new ArrayList<>();

        } catch (Exception e) {
            log.error("Failed to cleanup expired registrations: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Clean up old registrations for a vendor
     */
    @Transactional
    public List<String> cleanupOldRegistrationsByVendor(String vendorId) {
        try {
            List<RegistrationLog> oldestRegistrations = registrationLogRepository
                    .findOldestTotalRegistrationsByVendor(vendorId);

            // Remove oldest registrations to keep count under limit
            int registrationsToRemove = oldestRegistrations.size();
            if (registrationsToRemove > 0) {
                List<RegistrationLog> toRemove = oldestRegistrations.subList(0, registrationsToRemove);
                List<String> deviceIds = toRemove.stream()
                        .map(RegistrationLog::getDeviceId)
                        .collect(Collectors.toList());

                // Call ETX API to delete registrations first, then mark as inactive
                if (!deviceIds.isEmpty()) {
                    try {
                        // Make the ETX API call synchronous
                        registrationApi.deleteRegistrations(deviceIds, vendorId).block();

                        // Only mark as inactive if successfully deleted from ETX
                        toRemove.forEach(log -> {
                            log.setIsActive(false);
                            log.setUnregisterAt(Instant.now());
                        });
                        registrationLogRepository.saveAll(toRemove);
                        log.info("Successfully deleted {} registrations from ETX for vendor {} and marked as inactive",
                                deviceIds.size(), vendorId);
                        return deviceIds;
                    } catch (Exception e) {
                        log.error("Failed to delete registrations from ETX for vendor {}: {}", vendorId,
                                e.getMessage());
                        // Return empty list since ETX deletion failed
                        return new ArrayList<>();
                    }
                } else {
                    // If no device IDs, just mark as inactive
                    toRemove.forEach(log -> {
                        log.setIsActive(false);
                        log.setUnregisterAt(Instant.now());
                    });
                    registrationLogRepository.saveAll(toRemove);
                    log.info("Marked {} old registrations as inactive for vendor: {}", registrationsToRemove, vendorId);
                    return new ArrayList<>();
                }
            }
            return new ArrayList<>();

        } catch (Exception e) {
            log.error("Failed to cleanup old registrations for vendor {}: {}", vendorId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Get the default vendor limit from configuration
     */
    private int getDefaultVendorLimitFromConfig(String vendorId) {
        if (etxProperties.getRegistration().getVendors() != null) {
            return etxProperties.getRegistration().getVendors().stream()
                    .filter(vendor -> vendorId.equals(vendor.getId()))
                    .map(EtxProperties.VendorConfig::getLimit)
                    .findFirst()
                    .orElse(50); // Default limit if vendor not found
        }
        return 50; // Default limit if no vendors configured
    }

    /**
     * Get the default user limit from configuration
     */
    private int getDefaultUserLimitFromConfig(String vendorId) {
        if (etxProperties.getRegistration().getVendors() != null) {
            return etxProperties.getRegistration().getVendors().stream()
                    .filter(vendor -> vendorId.equals(vendor.getId()))
                    .map(EtxProperties.VendorConfig::getUserLimit)
                    .findFirst()
                    .orElse(5); // Default user limit if vendor not found
        }
        return 5; // Default user limit if no vendors configured
    }

    private Instant parseExpirationTime(String expirationTime) {
        try {
            return Instant.parse(expirationTime);
        } catch (Exception e) {
            log.warn("Failed to parse expiration time: {}, using default", expirationTime);
            return Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS);
        }
    }
}
