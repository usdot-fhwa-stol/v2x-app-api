package usdot.v2x.app.api.services;

import usdot.v2x.app.api.config.etx.EtxProperties;
import usdot.v2x.app.api.etx.registration.RegistrationApi;
import usdot.v2x.app.api.models.etx.registration.*;
import usdot.v2x.app.api.models.UserLimits;
import usdot.v2x.app.api.models.VendorLimits;
import usdot.v2x.app.api.repositories.RegistrationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.*;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RegistrationLogServiceTest {

    @Mock
    private RegistrationLogRepository registrationLogRepository;

    @Mock
    private RegistrationApi registrationApi;

    @Mock
    private EtxProperties etxProperties;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private GrantedAuthority grantedAuthority;

    @Mock
    private UserLimitsService userLimitsService;

    @Mock
    private VendorLimitsService vendorLimitsService;

    @InjectMocks
    private RegistrationLogService registrationLogService;

    @BeforeEach
    void setUp() {
        // Setup security context only for tests that need it
        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn("testuser");
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Collection authorities = Collections.singletonList(grantedAuthority);
        lenient().when(authentication.getAuthorities()).thenReturn(authorities);
        lenient().when(grantedAuthority.getAuthority()).thenReturn("ROLE_USER");

        // Setup EtxProperties mock
        EtxProperties.RegistrationLimits registrationLimits = new EtxProperties.RegistrationLimits();
        EtxProperties.VendorConfig vendorConfig = new EtxProperties.VendorConfig();
        vendorConfig.setId("test-vendor");
        vendorConfig.setLimit(50);
        vendorConfig.setUserLimit(5);
        registrationLimits.setVendors(Collections.singletonList(vendorConfig));
        lenient().when(etxProperties.getRegistration()).thenReturn(registrationLimits);
    }

    @Test
    void testLogRegistration_UserLimitNotExceeded() {
        // Arrange
        RegistrationResponse response = createRegistrationResponse();
        RegistrationClientType clientType = RegistrationClientType.Vehicle;
        RegistrationClientSubType clientSubtype = RegistrationClientSubType.PassengerCar;
        String vendorId = "test-vendor";
        String requestedBy = "testuser";

        when(registrationLogRepository.countActiveRegistrationsByUser(requestedBy)).thenReturn(3L);
        when(registrationLogRepository.countTotalActiveRegistrationsByVendor(vendorId)).thenReturn(10L);

        // Mock the new service calls
        UserLimits userLimits = new UserLimits();
        userLimits.setMaxRegistrations(5);
        when(userLimitsService.getUserLimits(requestedBy, vendorId, 5)).thenReturn(userLimits);

        VendorLimits vendorLimits = new VendorLimits();
        vendorLimits.setMaxRegistrations(50);
        when(vendorLimitsService.getVendorLimits(vendorId, 50)).thenReturn(vendorLimits);

        // Act
        registrationLogService.logRegistration(response, clientType, clientSubtype, vendorId, requestedBy);

        // Assert
        verify(registrationLogRepository).save(any(RegistrationLog.class));
        verify(registrationLogRepository, never()).findOldestRegistrationsByUser(anyString());
    }

    @Test
    void testLogRegistration_UserLimitExceeded() {
        // Arrange
        RegistrationResponse response = createRegistrationResponse();
        RegistrationClientType clientType = RegistrationClientType.Vehicle;
        RegistrationClientSubType clientSubtype = RegistrationClientSubType.PassengerCar;
        String vendorId = "test-vendor";
        String requestedBy = "testuser";

        when(registrationLogRepository.countActiveRegistrationsByUser(requestedBy)).thenReturn(5L);
        when(registrationLogRepository.countTotalActiveRegistrationsByVendor(vendorId)).thenReturn(10L);

        // Mock the new service calls
        UserLimits userLimits = new UserLimits();
        userLimits.setMaxRegistrations(5);
        when(userLimitsService.getUserLimits(requestedBy, vendorId, 5)).thenReturn(userLimits);

        VendorLimits vendorLimits = new VendorLimits();
        vendorLimits.setMaxRegistrations(50);
        when(vendorLimitsService.getVendorLimits(vendorId, 50)).thenReturn(vendorLimits);

        List<RegistrationLog> oldRegistrations = createOldRegistrations(7); // More than the limit of 5
        when(registrationLogRepository.findOldestRegistrationsByUser(requestedBy)).thenReturn(oldRegistrations);
        when(registrationLogRepository.countActiveRegistrationsByUser(requestedBy)).thenReturn(5L, 2L); // After cleanup
        when(registrationLogRepository.saveAll(anyList())).thenReturn(oldRegistrations);
        when(registrationApi.deleteRegistrations(anyList(), anyString())).thenReturn(Mono.empty());

        // Act
        registrationLogService.logRegistration(response, clientType, clientSubtype, vendorId, requestedBy);

        // Assert
        verify(registrationLogRepository).findOldestRegistrationsByUser(requestedBy);
        verify(registrationLogRepository).saveAll(anyList());
        verify(registrationApi).deleteRegistrations(anyList(), anyString());
    }

    @Test
    void testLogRegistration_VendorLimitExceeded() {
        // Arrange
        RegistrationResponse response = createRegistrationResponse();
        RegistrationClientType clientType = RegistrationClientType.Vehicle;
        RegistrationClientSubType clientSubtype = RegistrationClientSubType.PassengerCar;
        String vendorId = "test-vendor";
        String requestedBy = "testuser";

        when(registrationLogRepository.countActiveRegistrationsByUser(requestedBy)).thenReturn(3L);
        when(registrationLogRepository.countTotalActiveRegistrationsByVendor(vendorId)).thenReturn(50L);

        // Mock the new service calls
        UserLimits userLimits = new UserLimits();
        userLimits.setMaxRegistrations(5);
        when(userLimitsService.getUserLimits(requestedBy, vendorId, 5)).thenReturn(userLimits);

        VendorLimits vendorLimits = new VendorLimits();
        vendorLimits.setMaxRegistrations(50);
        when(vendorLimitsService.getVendorLimits(vendorId, 50)).thenReturn(vendorLimits);

        List<RegistrationLog> oldVendorRegistrations = createOldRegistrations(55); // More than the limit of 50
        when(registrationLogRepository.findOldestTotalRegistrationsByVendor(vendorId))
                .thenReturn(oldVendorRegistrations);
        when(registrationLogRepository.countTotalActiveRegistrationsByVendor(vendorId)).thenReturn(50L, 45L); // After
                                                                                                              // cleanup
        when(registrationLogRepository.saveAll(anyList())).thenReturn(oldVendorRegistrations);
        when(registrationApi.deleteRegistrations(anyList(), anyString())).thenReturn(Mono.empty());

        // Act
        registrationLogService.logRegistration(response, clientType, clientSubtype, vendorId, requestedBy);

        // Assert
        verify(registrationLogRepository).findOldestTotalRegistrationsByVendor(vendorId);
        verify(registrationLogRepository).saveAll(anyList());
        verify(registrationApi).deleteRegistrations(anyList(), anyString());
    }

    @Test
    void testCleanupOldRegistrations() {
        // Arrange
        String requestedBy = "testuser";
        String vendorId = "test-vendor";
        List<RegistrationLog> oldRegistrations = createOldRegistrations(7);
        when(registrationLogRepository.findOldestRegistrationsByUser(requestedBy)).thenReturn(oldRegistrations);
        when(registrationLogRepository.saveAll(anyList())).thenReturn(oldRegistrations);
        when(registrationApi.deleteRegistrations(anyList(), anyString())).thenReturn(Mono.empty());

        // Mock the new service calls
        UserLimits userLimits = new UserLimits();
        userLimits.setMaxRegistrations(5);
        when(userLimitsService.getUserLimits(requestedBy, vendorId, 5)).thenReturn(userLimits);

        // Act
        List<String> result = registrationLogService.cleanupOldRegistrations(requestedBy);

        // Assert
        assertEquals(3, result.size()); // Should remove 3 registrations (7 - 5 + 1 = 3)
        verify(registrationLogRepository).findOldestRegistrationsByUser(requestedBy);
        verify(registrationLogRepository).saveAll(anyList());
        verify(registrationApi).deleteRegistrations(anyList(), anyString());
    }

    @Test
    void testCleanupOldRegistrationsByVendor() {
        // Arrange
        String vendorId = "test-vendor";
        List<RegistrationLog> oldVendorRegistrations = createOldRegistrations(55); // More than the limit of 50
        when(registrationLogRepository.findOldestTotalRegistrationsByVendor(vendorId))
                .thenReturn(oldVendorRegistrations);
        when(registrationLogRepository.saveAll(anyList())).thenReturn(oldVendorRegistrations);
        when(registrationApi.deleteRegistrations(anyList(), anyString())).thenReturn(Mono.empty());

        // Act
        List<String> result = registrationLogService.cleanupOldRegistrationsByVendor(vendorId);

        // Assert
        assertEquals(55, result.size()); // Should remove all 55 registrations
        verify(registrationLogRepository).findOldestTotalRegistrationsByVendor(vendorId);
        verify(registrationLogRepository).saveAll(anyList());
        verify(registrationApi).deleteRegistrations(anyList(), anyString());
    }

    @Test
    void testCleanupExpiredRegistrations() {
        // Arrange
        List<RegistrationLog> expiredRegistrations = createOldRegistrations(5);
        // Ensure the mock objects have proper device IDs
        for (int i = 0; i < expiredRegistrations.size(); i++) {
            expiredRegistrations.get(i).setDeviceId("device" + i);
        }
        when(registrationLogRepository.findExpiredRegistrations(any(Instant.class))).thenReturn(expiredRegistrations);
        when(registrationLogRepository.saveAll(anyList())).thenReturn(expiredRegistrations);
        when(registrationApi.deleteRegistrations(anyList(), anyString())).thenReturn(Mono.empty());

        // Act
        List<String> result = registrationLogService.cleanupExpiredRegistrations();

        // Assert
        assertEquals(5, result.size());
        verify(registrationLogRepository).findExpiredRegistrations(any(Instant.class));
        verify(registrationLogRepository).saveAll(anyList());
        verify(registrationApi).deleteRegistrations(anyList(), anyString());
    }

    @Test
    void testGetActiveRegistrations() {
        // Arrange
        String requestedBy = "testuser";
        List<RegistrationLog> expectedLogs = createOldRegistrations(3);
        when(registrationLogRepository.findActiveRegistrationsByUser(requestedBy)).thenReturn(expectedLogs);

        // Act
        List<RegistrationLog> result = registrationLogService.getActiveRegistrations(requestedBy);

        // Assert
        assertEquals(expectedLogs, result);
        verify(registrationLogRepository).findActiveRegistrationsByUser(requestedBy);
    }

    @Test
    void testGetRegistrationCount() {
        // Arrange
        String requestedBy = "testuser";
        when(registrationLogRepository.countActiveRegistrationsByUser(requestedBy)).thenReturn(5L);

        // Act
        Long result = registrationLogService.getRegistrationCount(requestedBy);

        // Assert
        assertEquals(5L, result);
        verify(registrationLogRepository).countActiveRegistrationsByUser(requestedBy);
    }

    @Test
    void testLogRegistration_WithDepositorRole() {
        // Arrange
        RegistrationResponse response = createRegistrationResponse();
        RegistrationClientType clientType = RegistrationClientType.Vehicle;
        RegistrationClientSubType clientSubtype = RegistrationClientSubType.PassengerCar;
        String vendorId = "test-vendor";
        String requestedBy = "testuser";

        when(registrationLogRepository.countActiveRegistrationsByUser(requestedBy)).thenReturn(3L);
        when(registrationLogRepository.countTotalActiveRegistrationsByVendor(vendorId)).thenReturn(10L);

        // Mock the new service calls
        UserLimits userLimits = new UserLimits();
        userLimits.setMaxRegistrations(5);
        when(userLimitsService.getUserLimits(requestedBy, vendorId, 5)).thenReturn(userLimits);

        VendorLimits vendorLimits = new VendorLimits();
        vendorLimits.setMaxRegistrations(50);
        when(vendorLimitsService.getVendorLimits(vendorId, 50)).thenReturn(vendorLimits);

        // Act
        registrationLogService.logRegistration(response, clientType, clientSubtype, vendorId, requestedBy);

        // Assert
        ArgumentCaptor<RegistrationLog> logCaptor = ArgumentCaptor.forClass(RegistrationLog.class);
        verify(registrationLogRepository).save(logCaptor.capture());

        RegistrationLog savedLog = logCaptor.getValue();
        assertEquals(requestedBy, savedLog.getRequestedBy());
        assertEquals(clientType, savedLog.getClientType());
        assertEquals(clientSubtype, savedLog.getClientSubtype());
        assertEquals(vendorId, savedLog.getVendorId());
    }

    private RegistrationResponse createRegistrationResponse() {
        RegistrationResponse response = new RegistrationResponse();
        response.setDeviceId("test-device-id");

        Certificate certificate = new Certificate();
        certificate.setExpirationTime(Instant.now().plusSeconds(3600).toString());
        response.setCertificate(certificate);

        return response;
    }

    private List<RegistrationLog> createOldRegistrations(int count) {
        List<RegistrationLog> logs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RegistrationLog log = new RegistrationLog();
            log.setDeviceId("device" + i);
            log.setClientType(RegistrationClientType.Vehicle);
            log.setClientSubtype(RegistrationClientSubType.PassengerCar);
            log.setVendorId("test-vendor");
            log.setRequestedBy("testuser");
            log.setRegistrationCount(1);
            log.setCreatedAt(Instant.now().minusSeconds(3600 * (i + 1))); // Older timestamps
            log.setExpiresAt(Instant.now().plusSeconds(3600));
            log.setIsActive(true);
            logs.add(log);
        }
        return logs;
    }
}
