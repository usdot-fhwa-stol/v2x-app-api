package usdot.v2x.app.api.repositories;

import usdot.v2x.app.api.models.etx.registration.RegistrationEvent;
import usdot.v2x.app.api.models.etx.registration.RegistrationEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class RegistrationEventRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RegistrationEventRepository registrationEventRepository;

    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        entityManager.persistAndFlush(createEvent("device1", "vendor1", RegistrationEventType.REGISTER,
                now.minusSeconds(3600)));
        entityManager.persistAndFlush(createEvent("device1", "vendor1", RegistrationEventType.EVICT_VENDOR_LIMIT,
                now.minusSeconds(60)));
        entityManager.persistAndFlush(createEvent("device2", "vendor2", RegistrationEventType.CONNECT,
                now.minusSeconds(120)));
        entityManager.persistAndFlush(createEvent("device3", "vendor1", RegistrationEventType.CONNECT,
                now.minusSeconds(86400 * 2)));
    }

    @Test
    void testFindEvents_NoFiltersReturnsWindowNewestFirst() {
        List<RegistrationEvent> result = registrationEventRepository.findEvents(null, null,
                now.minusSeconds(86400), now, PageRequest.of(0, 100));

        assertEquals(3, result.size());
        assertEquals(RegistrationEventType.EVICT_VENDOR_LIMIT, result.get(0).getEventType());
        assertEquals(RegistrationEventType.REGISTER, result.get(2).getEventType());
    }

    @Test
    void testFindEvents_FilterByDevice() {
        List<RegistrationEvent> result = registrationEventRepository.findEvents("device1", null,
                now.minusSeconds(86400), now, PageRequest.of(0, 100));

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(e -> "device1".equals(e.getDeviceId())));
    }

    @Test
    void testFindEvents_FilterByVendorAndLimit() {
        List<RegistrationEvent> result = registrationEventRepository.findEvents(null, "vendor1",
                now.minusSeconds(86400), now, PageRequest.of(0, 1));

        assertEquals(1, result.size());
        assertEquals(RegistrationEventType.EVICT_VENDOR_LIMIT, result.get(0).getEventType());
    }

    @Test
    void testExistsByDeviceIdAndEventType() {
        assertTrue(registrationEventRepository.existsByDeviceIdAndEventType("device2",
                RegistrationEventType.CONNECT));
        assertFalse(registrationEventRepository.existsByDeviceIdAndEventType("device2",
                RegistrationEventType.UNTRACKED_IN_ETX));
    }

    private RegistrationEvent createEvent(String deviceId, String vendorId, RegistrationEventType type,
            Instant createdAt) {
        RegistrationEvent event = new RegistrationEvent();
        event.setDeviceId(deviceId);
        event.setVendorId(vendorId);
        event.setRequestedBy("user1");
        event.setEventType(type);
        event.setReason("test");
        event.setCreatedAt(createdAt);
        return event;
    }
}
