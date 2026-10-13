package usdot.v2x.app.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import usdot.v2x.app.api.config.etx.EtxProperties;
import usdot.v2x.app.api.models.etx.registration.ReconcileResult;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Scheduled service that removes expired ETX registrations and reconciles
 * registration_logs against the ETX active client list for each configured
 * vendor. The interval is configured via
 * {@code etx.registration.cleanup.interval} and supports Spring Boot duration
 * format (e.g., "5m", "30s", "1h") or ISO-8601 format.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationCleanupService {

    private final RegistrationLogService registrationLogService;
    private final EtxProperties etxProperties;

    @Scheduled(fixedRateString = "${etx.registration.cleanup.interval:5m}", initialDelayString = "${etx.registration.cleanup.initial-delay:1m}")
    public void cleanupAndReconcile() {
        if (!Boolean.TRUE.equals(etxProperties.getEnabled())
                || etxProperties.getRegistration() == null
                || !etxProperties.getRegistration().getCleanup().isEnabled()) {
            log.debug("Registration cleanup is disabled, skipping");
            return;
        }

        try {
            List<String> expired = registrationLogService.cleanupExpiredRegistrations();
            if (!expired.isEmpty()) {
                log.info("Scheduled registration cleanup removed {} expired registrations", expired.size());
            }
        } catch (Exception e) {
            log.error("Error during scheduled cleanup of expired registrations", e);
        }

        for (String vendorId : configuredVendorIds()) {
            try {
                ReconcileResult result = registrationLogService.reconcileWithEtx(vendorId);
                log.debug("Reconciled vendor {}: etx={} deactivated={} untracked={}", vendorId,
                        result.getEtxCount(), result.getDeactivated().size(), result.getUntrackedInEtx().size());
            } catch (Exception e) {
                log.warn("Failed to reconcile registrations with ETX for vendor {}: {}", vendorId, e.getMessage());
            }
        }
    }

    private Set<String> configuredVendorIds() {
        Set<String> vendorIds = new LinkedHashSet<>();
        if (etxProperties.getRegistration().getVendors() != null) {
            etxProperties.getRegistration().getVendors().stream()
                    .map(EtxProperties.VendorConfig::getId)
                    .filter(Objects::nonNull)
                    .filter(id -> !id.isBlank())
                    .forEach(vendorIds::add);
        }
        return vendorIds;
    }
}
