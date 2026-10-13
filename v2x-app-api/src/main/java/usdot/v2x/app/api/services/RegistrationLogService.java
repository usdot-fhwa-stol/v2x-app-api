package usdot.v2x.app.api.services;

import usdot.v2x.app.api.config.etx.EtxProperties;
import usdot.v2x.app.api.etx.registration.RegistrationApi;
import usdot.v2x.app.api.models.UserLimits;
import usdot.v2x.app.api.models.VendorLimits;
import usdot.v2x.app.api.models.etx.ErrorResponseException;
import usdot.v2x.app.api.models.etx.registration.ActiveRegistrationView;
import usdot.v2x.app.api.models.etx.registration.ActiveRegistrationsResponse;
import usdot.v2x.app.api.models.etx.registration.EtxClientRecord;
import usdot.v2x.app.api.models.etx.registration.ReconcileResult;
import usdot.v2x.app.api.models.etx.registration.RegistrationCheckResponse;
import usdot.v2x.app.api.models.etx.registration.RegistrationClientSubType;
import usdot.v2x.app.api.models.etx.registration.RegistrationClientType;
import usdot.v2x.app.api.models.etx.registration.RegistrationEventType;
import usdot.v2x.app.api.models.etx.registration.RegistrationLog;
import usdot.v2x.app.api.models.etx.registration.RegistrationResponse;
import usdot.v2x.app.api.repositories.RegistrationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for managing ETX registration logs and operations.
 */
@Slf4j
@Service
public class RegistrationLogService {

    /** Upper bound on blocking ETX calls made from cleanup/reconcile paths. */
    private static final Duration ETX_TIMEOUT = Duration.ofSeconds(30);

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

    @Autowired
    private RegistrationEventService registrationEventService;

    /**
     * Log a successful registration. Capacity is enforced beforehand by
     * {@link #ensureCapacityForRegistration}; this only records the result. If
     * ETX returned a device ID that is already tracked as active, that row is
     * updated rather than duplicated.
     */
    @Transactional
    public void logRegistration(RegistrationResponse registrationResponse, RegistrationClientType clientType,
            RegistrationClientSubType clientSubtype,
            String vendorId,
            String requestedBy) {
        try {
            String deviceId = registrationResponse.getDeviceId();
            Instant now = Instant.now();
            Instant expiresAt = parseExpirationTime(registrationResponse.getCertificate() != null
                    ? registrationResponse.getCertificate().getExpirationTime()
                    : null);
            Long userCount = registrationLogRepository.countActiveRegistrationsByUserAndVendor(requestedBy, vendorId);

            List<RegistrationLog> existing = registrationLogRepository.findByDeviceIds(List.of(deviceId));
            RegistrationLog logEntry;
            if (!existing.isEmpty()) {
                logEntry = existing.get(0);
                log.info("Device {} is already tracked as active, updating existing registration log", deviceId);
            } else {
                logEntry = new RegistrationLog();
                logEntry.setDeviceId(deviceId);
                logEntry.setCreatedAt(now);
                logEntry.setRegistrationCount(userCount.intValue() + 1);
                logEntry.setIsActive(true);
            }
            logEntry.setClientType(clientType);
            logEntry.setClientSubtype(clientSubtype);
            logEntry.setVendorId(vendorId);
            logEntry.setRequestedBy(requestedBy);
            logEntry.setExpiresAt(expiresAt);
            // Set initial lastConnected timestamp to registration time
            logEntry.setLastConnected(now);

            registrationLogRepository.save(logEntry);
            registrationEventService.record(RegistrationEventType.REGISTER, deviceId, vendorId, requestedBy,
                    existing.isEmpty() ? "new registration" : "re-registration of tracked device", null,
                    "expiresAt=" + expiresAt);

            log.info("Logged registration for user: {}, device: {}, type: {}, vendor: {}, userCount: {}",
                    requestedBy, deviceId, clientType, vendorId, existing.isEmpty() ? userCount + 1 : userCount);

        } catch (Exception e) {
            log.error("Failed to log registration: {}", e.getMessage(), e);
        }
    }

    /**
     * Record a successful registration update (PUT). Refreshes the expiration
     * and last connected timestamps of the tracked device without touching
     * capacity, since an update does not add a registration.
     */
    @Transactional
    public void logRegistrationUpdate(RegistrationResponse registrationResponse, String deviceId, String vendorId,
            String requestedBy) {
        try {
            String responseDeviceId = registrationResponse.getDeviceId() != null ? registrationResponse.getDeviceId()
                    : deviceId;
            Instant expiresAt = registrationResponse.getCertificate() != null
                    ? parseExpirationTime(registrationResponse.getCertificate().getExpirationTime())
                    : null;

            List<RegistrationLog> existing = registrationLogRepository.findByDeviceIds(List.of(responseDeviceId));
            if (existing.isEmpty()) {
                log.warn("Registration update for device {} which is not tracked locally", responseDeviceId);
            } else {
                Instant now = Instant.now();
                existing.forEach(entry -> {
                    if (expiresAt != null) {
                        entry.setExpiresAt(expiresAt);
                    }
                    entry.setLastConnected(now);
                });
                registrationLogRepository.saveAll(existing);
            }
            registrationEventService.record(RegistrationEventType.PUT, responseDeviceId, vendorId, requestedBy,
                    existing.isEmpty() ? "device not tracked locally" : "registration renewed", null,
                    "expiresAt=" + expiresAt);
        } catch (Exception e) {
            log.error("Failed to log registration update for device {}: {}", deviceId, e.getMessage(), e);
        }
    }

    /**
     * Clean up a user's oldest registrations under a vendor so one new
     * registration fits within the user's limit
     */
    @Transactional
    public List<String> cleanupOldRegistrations(String requestedBy, String vendorId) {
        try {
            List<RegistrationLog> oldestRegistrations = registrationLogRepository
                    .findOldestRegistrationsByUserAndVendor(requestedBy, vendorId);

            int defaultUserLimit = getDefaultUserLimitFromConfig(vendorId);
            UserLimits userLimits = userLimitsService.getUserLimits(requestedBy, vendorId, defaultUserLimit);
            int userLimit = userLimits.getMaxRegistrations();

            // Remove oldest registrations to leave room for one more under the limit
            int registrationsToRemove = oldestRegistrations.size() - userLimit + 1;
            if (registrationsToRemove <= 0) {
                return new ArrayList<>();
            }
            List<RegistrationLog> toRemove = oldestRegistrations.subList(0, registrationsToRemove);
            String reason = String.format("user %s at limit %d (active %d)", requestedBy, userLimit,
                    oldestRegistrations.size());
            return evict(toRemove, vendorId, RegistrationEventType.EVICT_USER_LIMIT, reason);

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
     * Get registration count for a user under a specific vendor
     */
    public Long getRegistrationCount(String requestedBy, String vendorId) {
        return registrationLogRepository.countActiveRegistrationsByUserAndVendor(requestedBy, vendorId);
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
     * Local tracking is first reconciled against ETX so that registrations ETX
     * no longer holds do not count against the limits.
     */
    @Transactional
    public void ensureCapacityForRegistration(String vendorId, String requestedBy) {
        if (cleanupConfig().isReconcileOnRegister()) {
            try {
                reconcileWithEtx(vendorId);
            } catch (Exception e) {
                log.warn("Failed to reconcile registrations with ETX for vendor {}, using local counts: {}",
                        vendorId, e.getMessage());
            }
        }

        try {
            // Get default limits from configuration as fallback
            int defaultUserLimit = getDefaultUserLimitFromConfig(vendorId);
            int defaultVendorLimit = getDefaultVendorLimitFromConfig(vendorId);

            // Check USER limits (how many registrations this specific user can have)
            // This gets the actual limit from the database, using config as fallback
            UserLimits userLimits = userLimitsService.getUserLimits(requestedBy, vendorId, defaultUserLimit);
            Long userCount = getRegistrationCount(requestedBy, vendorId);
            int userLimit = userLimits.getMaxRegistrations();
            if (userCount >= userLimit) {
                log.info("User {} has reached their personal limit ({}), cleaning up their oldest registrations",
                        requestedBy, userLimit);
                cleanupOldRegistrations(requestedBy, vendorId);
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
     * Clean up expired registrations. Each expired row is first checked against
     * ETX: a certificate that ETX reports as renewed only refreshes the local
     * expiration, a device ETX no longer knows is marked inactive, and the rest
     * are deleted from ETX before being marked inactive.
     */
    @Transactional
    public List<String> cleanupExpiredRegistrations() {
        List<String> removed = new ArrayList<>();
        try {
            Instant now = Instant.now();
            List<RegistrationLog> expiredRegistrations = registrationLogRepository.findExpiredRegistrations(now);
            if (expiredRegistrations.isEmpty()) {
                return removed;
            }

            expiredRegistrations.stream()
                    .collect(Collectors.groupingBy(RegistrationLog::getVendorId))
                    .forEach((vendorId, vendorRegistrations) -> removed
                            .addAll(cleanupExpiredForVendor(vendorId, vendorRegistrations, now)));
            return removed;

        } catch (Exception e) {
            log.error("Failed to cleanup expired registrations: {}", e.getMessage(), e);
            return removed;
        }
    }

    private List<String> cleanupExpiredForVendor(String vendorId, List<RegistrationLog> registrations,
            Instant now) {
        List<RegistrationLog> renewed = new ArrayList<>();
        List<RegistrationLog> goneFromEtx = new ArrayList<>();
        List<RegistrationLog> toDelete = new ArrayList<>();

        for (RegistrationLog registration : registrations) {
            try {
                RegistrationCheckResponse etx = registrationApi
                        .checkRegistration(registration.getDeviceId(), vendorId)
                        .block(ETX_TIMEOUT);
                Instant etxExpiry = etx != null && etx.getCertificate() != null
                        ? parseExpirationTimeOrNull(etx.getCertificate().getExpirationTime())
                        : null;
                if (etxExpiry != null && etxExpiry.isAfter(now)) {
                    registration.setExpiresAt(etxExpiry);
                    renewed.add(registration);
                } else {
                    toDelete.add(registration);
                }
            } catch (ErrorResponseException e) {
                if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                    goneFromEtx.add(registration);
                } else {
                    log.warn("Could not verify expired device {} with ETX, will retry next run: {}",
                            registration.getDeviceId(), e.getMessage());
                }
            } catch (Exception e) {
                log.warn("Could not verify expired device {} with ETX, will retry next run: {}",
                        registration.getDeviceId(), e.getMessage());
            }
        }

        if (!renewed.isEmpty()) {
            registrationLogRepository.saveAll(renewed);
            log.info("Refreshed expiration for {} renewed registrations for vendor {}", renewed.size(), vendorId);
        }

        List<String> removed = new ArrayList<>();
        if (!goneFromEtx.isEmpty()) {
            markInactive(goneFromEtx);
            registrationEventService.recordForRegistrations(RegistrationEventType.EXPIRE, goneFromEtx,
                    "expired and no longer registered in ETX", HttpStatus.NOT_FOUND.value());
            goneFromEtx.forEach(r -> removed.add(r.getDeviceId()));
        }
        if (!toDelete.isEmpty()) {
            removed.addAll(evict(toDelete, vendorId, RegistrationEventType.EXPIRE, "certificate expired"));
        }
        return removed;
    }

    /**
     * Clean up a vendor's oldest registrations so one new registration fits
     * within the vendor limit
     */
    @Transactional
    public List<String> cleanupOldRegistrationsByVendor(String vendorId) {
        try {
            List<RegistrationLog> oldestRegistrations = registrationLogRepository
                    .findOldestTotalRegistrationsByVendor(vendorId);

            int defaultVendorLimit = getDefaultVendorLimitFromConfig(vendorId);
            VendorLimits vendorLimits = vendorLimitsService.getVendorLimits(vendorId, defaultVendorLimit);
            int vendorLimit = vendorLimits.getMaxRegistrations();

            // Remove oldest registrations to leave room for one more under the limit
            int registrationsToRemove = oldestRegistrations.size() - vendorLimit + 1;
            if (registrationsToRemove <= 0) {
                return new ArrayList<>();
            }
            List<RegistrationLog> toRemove = oldestRegistrations.subList(0, registrationsToRemove);
            String reason = String.format("vendor %s at limit %d (active %d)", vendorId, vendorLimit,
                    oldestRegistrations.size());
            return evict(toRemove, vendorId, RegistrationEventType.EVICT_VENDOR_LIMIT, reason);

        } catch (Exception e) {
            log.error("Failed to cleanup old registrations for vendor {}: {}", vendorId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Reconcile local tracking against the clients ETX reports for a vendor.
     * Local rows ETX no longer holds are marked inactive; ETX clients with no
     * local row are reported (once each) but never deleted, since they may have
     * been registered by another tool. Throws if ETX cannot be queried.
     */
    @Transactional
    public ReconcileResult reconcileWithEtx(String vendorId) {
        List<EtxClientRecord> etxClients = registrationApi.queryActiveClients(vendorId).block(ETX_TIMEOUT);
        if (etxClients == null) {
            etxClients = List.of();
        }
        Set<String> etxIds = etxClients.stream()
                .map(EtxClientRecord::getDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<RegistrationLog> localActive = registrationLogRepository.findActiveByVendor(vendorId);
        Set<String> localIds = localActive.stream()
                .map(RegistrationLog::getDeviceId)
                .collect(Collectors.toSet());

        Instant graceCutoff = Instant.now().minus(cleanupConfig().getReconcileGracePeriod());
        List<RegistrationLog> missing = localActive.stream()
                .filter(r -> !etxIds.contains(r.getDeviceId()))
                .filter(r -> r.getCreatedAt() == null || r.getCreatedAt().isBefore(graceCutoff))
                .toList();

        ReconcileResult result = new ReconcileResult();
        result.setVendorId(vendorId);
        result.setEtxCount(etxIds.size());

        if (!missing.isEmpty()) {
            markInactive(missing);
            registrationEventService.recordForRegistrations(RegistrationEventType.RECONCILED_MISSING, missing,
                    "no longer reported by ETX client query", null);
            missing.forEach(r -> result.getDeactivated().add(r.getDeviceId()));
            log.warn("Reconcile vendor {}: marked {} local registrations inactive that ETX no longer reports",
                    vendorId, missing.size());
        }

        etxIds.stream()
                .filter(id -> !localIds.contains(id))
                .sorted()
                .forEach(id -> {
                    result.getUntrackedInEtx().add(id);
                    if (!registrationEventService.hasEvent(id, RegistrationEventType.UNTRACKED_IN_ETX)) {
                        registrationEventService.record(RegistrationEventType.UNTRACKED_IN_ETX, id, vendorId, null,
                                "registered in ETX with no active local registration log");
                    }
                });
        if (!result.getUntrackedInEtx().isEmpty()) {
            log.warn("Reconcile vendor {}: {} ETX registrations are not tracked locally", vendorId,
                    result.getUntrackedInEtx().size());
        }
        return result;
    }

    /**
     * Active registrations for a vendor as reported by ETX, merged with local
     * tracking data
     */
    public Mono<ActiveRegistrationsResponse> getActiveRegistrationsView(String vendorId) {
        return registrationApi.queryActiveClients(vendorId)
                // The local lookup is a blocking JDBC call; keep it off the HTTP client thread
                .publishOn(Schedulers.boundedElastic())
                .map(etxClients -> buildActiveRegistrationsView(vendorId, etxClients,
                        registrationLogRepository.findActiveByVendor(vendorId)));
    }

    static ActiveRegistrationsResponse buildActiveRegistrationsView(String vendorId,
            List<EtxClientRecord> etxClients, List<RegistrationLog> localActive) {
        Map<String, RegistrationLog> localById = localActive.stream()
                .collect(Collectors.toMap(RegistrationLog::getDeviceId, Function.identity(), (a, b) -> a));

        ActiveRegistrationsResponse response = new ActiveRegistrationsResponse();
        response.setVendorId(vendorId);
        response.setEtxCount(etxClients.size());
        response.setLocalActiveCount(localById.size());

        for (EtxClientRecord client : etxClients) {
            ActiveRegistrationView view = new ActiveRegistrationView();
            view.setDeviceId(client.getDeviceId());
            view.setClientType(client.getClientType());
            view.setClientSubtype(client.getClientSubtype());
            view.setMecIds(client.getMecIds());

            RegistrationLog local = localById.get(client.getDeviceId());
            view.setTrackedLocally(local != null);
            if (local != null) {
                view.setRequestedBy(local.getRequestedBy());
                view.setCreatedAt(local.getCreatedAt());
                view.setExpiresAt(local.getExpiresAt());
                view.setLastConnected(local.getLastConnected());
            } else {
                response.getUntrackedInEtx().add(client.getDeviceId());
            }
            response.getItems().add(view);
        }

        Set<String> etxIds = etxClients.stream().map(EtxClientRecord::getDeviceId).collect(Collectors.toSet());
        localById.keySet().stream()
                .filter(id -> !etxIds.contains(id))
                .sorted()
                .forEach(response.getStaleLocal()::add);
        return response;
    }

    /**
     * Delete registrations from ETX, then mark them inactive locally. The event
     * is recorded before the delete so the intent is captured even if the call
     * hangs; a failed delete is recorded and leaves the rows active.
     */
    private List<String> evict(List<RegistrationLog> toRemove, String vendorId, RegistrationEventType eventType,
            String reason) {
        List<String> deviceIds = toRemove.stream()
                .map(RegistrationLog::getDeviceId)
                .collect(Collectors.toList());
        if (deviceIds.isEmpty()) {
            return new ArrayList<>();
        }

        registrationEventService.recordForRegistrations(eventType, toRemove, reason, null);
        try {
            registrationApi.deleteRegistrations(deviceIds, vendorId).block(ETX_TIMEOUT);
        } catch (Exception e) {
            log.error("Failed to delete {} registrations from ETX for vendor {}: {}", deviceIds.size(), vendorId,
                    e.getMessage());
            registrationEventService.recordForRegistrations(RegistrationEventType.DELETE_FAILED, toRemove,
                    eventType + ": " + e.getMessage(), RegistrationEventService.etxStatusOf(e));
            return new ArrayList<>();
        }

        // Only mark as inactive once successfully deleted from ETX
        markInactive(toRemove);
        log.info("Deleted {} registrations from ETX for vendor {} ({}): {}", deviceIds.size(), vendorId, reason,
                deviceIds);
        return deviceIds;
    }

    private void markInactive(List<RegistrationLog> registrations) {
        Instant now = Instant.now();
        registrations.forEach(r -> {
            r.setIsActive(false);
            r.setUnregisterAt(now);
        });
        registrationLogRepository.saveAll(registrations);
    }

    private EtxProperties.RegistrationCleanup cleanupConfig() {
        EtxProperties.RegistrationLimits registration = etxProperties.getRegistration();
        if (registration == null || registration.getCleanup() == null) {
            return new EtxProperties.RegistrationCleanup();
        }
        return registration.getCleanup();
    }

    /**
     * Get the default vendor limit from configuration
     */
    private int getDefaultVendorLimitFromConfig(String vendorId) {
        return findVendorConfig(vendorId)
                .map(EtxProperties.VendorConfig::getLimit)
                .orElse(50); // Default limit if vendor not found
    }

    /**
     * Get the default user limit from configuration
     */
    private int getDefaultUserLimitFromConfig(String vendorId) {
        return findVendorConfig(vendorId)
                .map(EtxProperties.VendorConfig::getUserLimit)
                .orElse(5); // Default user limit if vendor not found
    }

    private Optional<EtxProperties.VendorConfig> findVendorConfig(String vendorId) {
        if (vendorId == null || etxProperties.getRegistration() == null
                || etxProperties.getRegistration().getVendors() == null) {
            return Optional.empty();
        }
        return etxProperties.getRegistration().getVendors().stream()
                .filter(vendor -> vendorId.equals(vendor.getId()))
                .findFirst();
    }

    private Instant parseExpirationTime(String expirationTime) {
        Instant parsed = parseExpirationTimeOrNull(expirationTime);
        if (parsed == null) {
            log.warn("Failed to parse expiration time: {}, using default", expirationTime);
            return Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS);
        }
        return parsed;
    }

    private Instant parseExpirationTimeOrNull(String expirationTime) {
        try {
            return expirationTime != null ? Instant.parse(expirationTime) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
