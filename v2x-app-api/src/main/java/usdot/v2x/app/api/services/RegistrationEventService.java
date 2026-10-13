package usdot.v2x.app.api.services;

import usdot.v2x.app.api.models.etx.ErrorResponseException;
import usdot.v2x.app.api.models.etx.registration.RegistrationEvent;
import usdot.v2x.app.api.models.etx.registration.RegistrationEventType;
import usdot.v2x.app.api.models.etx.registration.RegistrationLog;
import usdot.v2x.app.api.repositories.RegistrationEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Records registration lifecycle events. Writes run in their own transaction
 * and never throw, so recording an event cannot break or roll back the
 * operation being recorded.
 */
@Slf4j
@Service
public class RegistrationEventService {

    private final RegistrationEventRepository registrationEventRepository;
    private final TransactionTemplate requiresNew;

    public RegistrationEventService(RegistrationEventRepository registrationEventRepository,
            PlatformTransactionManager transactionManager) {
        this.registrationEventRepository = registrationEventRepository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(RegistrationEventType eventType, String deviceId, String vendorId, String requestedBy,
            String reason, Integer etxStatus, String details) {
        save(List.of(build(eventType, deviceId, vendorId, requestedBy, reason, etxStatus, details)));
    }

    public void record(RegistrationEventType eventType, String deviceId, String vendorId, String requestedBy,
            String reason) {
        record(eventType, deviceId, vendorId, requestedBy, reason, null, null);
    }

    /**
     * Record one event per registration, capturing its timestamps in details so
     * an eviction can later be checked against the device's activity.
     */
    public void recordForRegistrations(RegistrationEventType eventType, List<RegistrationLog> registrations,
            String reason, Integer etxStatus) {
        save(registrations.stream()
                .map(r -> build(eventType, r.getDeviceId(), r.getVendorId(), r.getRequestedBy(), reason, etxStatus,
                        describe(r)))
                .toList());
    }

    /**
     * Record an ETX call failure, extracting the HTTP status when available
     */
    public void recordFailure(RegistrationEventType eventType, String deviceId, String vendorId, String requestedBy,
            Throwable error) {
        record(eventType, deviceId, vendorId, requestedBy, error.getMessage(), etxStatusOf(error), null);
    }

    public boolean hasEvent(String deviceId, RegistrationEventType eventType) {
        return registrationEventRepository.existsByDeviceIdAndEventType(deviceId, eventType);
    }

    public List<RegistrationEvent> findEvents(String deviceId, String vendorId, Instant from, Instant to,
            int limit) {
        return registrationEventRepository.findEvents(deviceId, vendorId, from, to, PageRequest.of(0, limit));
    }

    public static Integer etxStatusOf(Throwable error) {
        return error instanceof ErrorResponseException ere ? ere.getStatusCode().value() : null;
    }

    private void save(List<RegistrationEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        try {
            requiresNew.executeWithoutResult(status -> registrationEventRepository.saveAll(events));
            events.forEach(e -> log.info("Registration event {} device={} vendor={} user={} reason={}",
                    e.getEventType(), e.getDeviceId(), e.getVendorId(), e.getRequestedBy(), e.getReason()));
        } catch (Exception e) {
            log.error("Failed to record {} registration event(s) of type {}: {}", events.size(),
                    events.get(0).getEventType(), e.getMessage());
        }
    }

    private RegistrationEvent build(RegistrationEventType eventType, String deviceId, String vendorId,
            String requestedBy, String reason, Integer etxStatus, String details) {
        RegistrationEvent event = new RegistrationEvent();
        event.setEventType(eventType);
        event.setDeviceId(deviceId);
        event.setVendorId(vendorId);
        event.setRequestedBy(requestedBy);
        event.setReason(reason);
        event.setEtxStatus(etxStatus);
        event.setDetails(details);
        event.setCreatedAt(Instant.now());
        return event;
    }

    private String describe(RegistrationLog registration) {
        return String.format("createdAt=%s lastConnected=%s expiresAt=%s",
                registration.getCreatedAt(), registration.getLastConnected(), registration.getExpiresAt());
    }
}
