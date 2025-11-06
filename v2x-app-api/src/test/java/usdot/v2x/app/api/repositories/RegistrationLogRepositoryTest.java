package usdot.v2x.app.api.repositories;

import usdot.v2x.app.api.models.etx.registration.RegistrationClientSubType;
import usdot.v2x.app.api.models.etx.registration.RegistrationClientType;
import usdot.v2x.app.api.models.etx.registration.RegistrationLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class RegistrationLogRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RegistrationLogRepository registrationLogRepository;

    private RegistrationLog activeDeviceLog;
    private RegistrationLog activePlatformLog;
    private RegistrationLog inactiveLog;
    private RegistrationLog expiredLog;

    @BeforeEach
    void setUp() {
        // Create test data
        activeDeviceLog = createRegistrationLog("device1", "user1", "vendor1",
                RegistrationClientType.Vehicle, true, Instant.now().minusSeconds(3600));

        activePlatformLog = createRegistrationLog("device2", "user1", "vendor1",
                RegistrationClientType.Software, true, Instant.now().minusSeconds(1800));

        inactiveLog = createRegistrationLog("device3", "user1", "vendor1",
                RegistrationClientType.Vehicle, false, Instant.now().minusSeconds(7200));

        expiredLog = createRegistrationLog("device4", "user2", "vendor3",
                RegistrationClientType.Vehicle, true, Instant.now().minusSeconds(7200));
        expiredLog.setExpiresAt(Instant.now().minusSeconds(3600)); // Expired

        // Persist test data
        entityManager.persistAndFlush(activeDeviceLog);
        entityManager.persistAndFlush(activePlatformLog);
        entityManager.persistAndFlush(inactiveLog);
        entityManager.persistAndFlush(expiredLog);
    }

    @Test
    void testFindActiveRegistrationsByUser() {
        // Act
        List<RegistrationLog> result = registrationLogRepository.findActiveRegistrationsByUser("user1");

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(RegistrationLog::getIsActive));
        assertTrue(result.stream().allMatch(log -> "user1".equals(log.getRequestedBy())));
    }

    @Test
    void testCountActiveRegistrationsByUser() {
        // Act
        Long count = registrationLogRepository.countActiveRegistrationsByUser("user1");

        // Assert
        assertEquals(2L, count);
    }

    @Test
    void testCountActiveRegistrationsByUser_NoRegistrations() {
        // Act
        Long count = registrationLogRepository.countActiveRegistrationsByUser("nonexistent");

        // Assert
        assertEquals(0L, count);
    }

    @Test
    void testFindExpiredRegistrations() {
        // Act
        List<RegistrationLog> result = registrationLogRepository.findExpiredRegistrations(Instant.now());

        // Assert
        assertEquals(1, result.size());
        assertEquals("device4", result.get(0).getDeviceId());
        assertTrue(result.get(0).getIsActive());
    }

    @Test
    void testFindOldestRegistrationsByUser() {
        // Act
        List<RegistrationLog> result = registrationLogRepository.findOldestRegistrationsByUser("user1");

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(RegistrationLog::getIsActive));
        assertTrue(result.stream().allMatch(log -> "user1".equals(log.getRequestedBy())));
        // Should be ordered by createdAt ASC (oldest first)
        assertTrue(result.get(0).getCreatedAt().isBefore(result.get(1).getCreatedAt()));
    }

    @Test
    void testFindByDeviceIds() {
        // Act
        List<RegistrationLog> result = registrationLogRepository.findByDeviceIds(
                List.of("device1", "device2"));

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(RegistrationLog::getIsActive));
        assertTrue(result.stream().anyMatch(log -> "device1".equals(log.getDeviceId())));
        assertTrue(result.stream().anyMatch(log -> "device2".equals(log.getDeviceId())));
    }

    @Test
    void testCountTotalActiveRegistrationsByVendor() {
        // Act
        Long count = registrationLogRepository.countTotalActiveRegistrationsByVendor("vendor1");

        // Assert
        assertEquals(2L, count); // Both Vehicle and Software types
    }

    @Test
    void testFindOldestTotalRegistrationsByVendor() {
        // Act
        List<RegistrationLog> result = registrationLogRepository.findOldestTotalRegistrationsByVendor("vendor1");

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(RegistrationLog::getIsActive));
        assertTrue(result.stream().allMatch(log -> "vendor1".equals(log.getVendorId())));
        // Should be ordered by createdAt ASC (oldest first)
        assertTrue(result.get(0).getCreatedAt().isBefore(result.get(1).getCreatedAt()));
    }

    @Test
    void testCountTotalActiveRegistrationsByVendor_NoRegistrations() {
        // Act
        Long count = registrationLogRepository.countTotalActiveRegistrationsByVendor("vendor2");

        // Assert
        assertEquals(0L, count); // vendor2 only has expired registrations
    }

    private RegistrationLog createRegistrationLog(String deviceId, String requestedBy, String vendorId,
            RegistrationClientType clientType, boolean isActive, Instant createdAt) {
        RegistrationLog log = new RegistrationLog();
        log.setDeviceId(deviceId);
        log.setClientType(clientType);
        log.setClientSubtype(RegistrationClientSubType.PassengerCar);
        log.setVendorId(vendorId);
        log.setRequestedBy(requestedBy);
        log.setRegistrationCount(1);
        log.setCreatedAt(createdAt);
        log.setExpiresAt(Instant.now().plusSeconds(3600));
        log.setIsActive(isActive);
        return log;
    }
}
