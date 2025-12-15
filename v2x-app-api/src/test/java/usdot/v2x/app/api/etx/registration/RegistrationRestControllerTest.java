package usdot.v2x.app.api.etx.registration;

import usdot.v2x.app.api.models.etx.registration.*;
import usdot.v2x.app.api.services.RegistrationLogService;
import usdot.v2x.app.api.utils.SecurityContextUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RegistrationRestControllerTest {

    @Mock
    private RegistrationApi registrationApi;

    @Mock
    private RegistrationLogService registrationLogService;

    @Mock
    private SecurityContextUtils securityContextUtils;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private GrantedAuthority grantedAuthority;

    private RegistrationRestController controller;

    @BeforeEach
    void setUp() {
        controller = new RegistrationRestController(registrationApi);

        // Use reflection to inject the services
        try {
            var serviceField = RegistrationRestController.class.getDeclaredField("registrationLogService");
            serviceField.setAccessible(true);
            serviceField.set(controller, registrationLogService);

            var securityContextUtilsField = RegistrationRestController.class.getDeclaredField("securityContextUtils");
            securityContextUtilsField.setAccessible(true);
            securityContextUtilsField.set(controller, securityContextUtils);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject services", e);
        }

        // Setup SecurityContextUtils mock
        lenient().when(securityContextUtils.determineVendorId()).thenReturn("test-vendor-id");
        lenient().when(securityContextUtils.determineRequestedBy()).thenReturn("testuser");

        // Setup security context
        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn("testuser");
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Collection authorities = Collections.singletonList(grantedAuthority);
        lenient().when(authentication.getAuthorities()).thenReturn(authorities);
        lenient().when(grantedAuthority.getAuthority()).thenReturn("ROLE_USER");
    }

    @Test
    void testPostRegistration_Success() {
        // Arrange
        RegistrationPostRequest request = new RegistrationPostRequest();
        request.setClientType(RegistrationClientType.Vehicle);
        request.setClientSubtype(RegistrationClientSubType.PassengerCar);

        RegistrationResponse expectedResponse = new RegistrationResponse();
        when(registrationApi.clientRegistrationRetryPending(any(RegistrationPostRequest.class), anyInt(), anyInt()))
                .thenReturn(reactor.core.publisher.Mono.just(expectedResponse));

        // Act
        reactor.core.publisher.Mono<RegistrationResponse> result = controller.postRegistration(
                request, true, 5, 15);

        // Assert
        assertNotNull(result);
        verify(registrationApi).clientRegistrationRetryPending(request, 5, 15);
    }

    @Test
    void testPostRegistration_WithRetry() {
        // Arrange
        RegistrationPostRequest request = new RegistrationPostRequest();
        request.setClientType(RegistrationClientType.Vehicle);
        request.setClientSubtype(RegistrationClientSubType.PassengerCar);

        RegistrationResponse expectedResponse = new RegistrationResponse();
        when(registrationApi.clientRegistrationRetryPending(any(RegistrationPostRequest.class), anyInt(), anyInt()))
                .thenReturn(reactor.core.publisher.Mono.just(expectedResponse));

        // Act
        reactor.core.publisher.Mono<RegistrationResponse> result = controller.postRegistration(
                request, true, 3, 10);

        // Assert
        assertNotNull(result);
        verify(registrationApi).clientRegistrationRetryPending(request, 3, 10);
    }

    @Test
    void testPutRegistration_Success() {
        // Arrange
        RegistrationPutRequest request = new RegistrationPutRequest();
        request.setDeviceId("test-device-id");

        RegistrationResponse expectedResponse = new RegistrationResponse();
        when(registrationApi.clientRegistrationPutRetryPending(any(RegistrationPutRequest.class), anyInt(), anyInt()))
                .thenReturn(reactor.core.publisher.Mono.just(expectedResponse));

        // Act
        reactor.core.publisher.Mono<RegistrationResponse> result = controller.putRegistration(
                true, 5, 15, request);

        // Assert
        assertNotNull(result);
        verify(registrationApi).clientRegistrationPutRetryPending(request, 5, 15);
    }

    @Test
    void testPostConnection_Success() {
        // Arrange
        ConnectionPostRequest request = new ConnectionPostRequest();
        request.setDeviceId("test-device-id");
        request.setLat(40.7128);
        request.setLon(-74.0060);
        request.setNetworkType(usdot.v2x.app.api.models.etx.NetworkType.NON_VZ);

        ConnectionResponse expectedResponse = new ConnectionResponse();
        when(registrationApi.clientConnectionPost(any(ConnectionPostRequest.class)))
                .thenReturn(reactor.core.publisher.Mono.just(expectedResponse));

        // Act
        reactor.core.publisher.Mono<ConnectionResponse> result = controller.postConnection(request);

        // Assert
        assertNotNull(result);
        verify(registrationApi).clientConnectionPost(request);
    }

    @Test
    void testPostRegistrationConnection_Success() {
        // Arrange
        RegistrationConnectionPostRequest request = new RegistrationConnectionPostRequest();
        request.setClientType(RegistrationClientType.Vehicle);
        request.setClientSubtype(RegistrationClientSubType.PassengerCar);
        request.setLat(40.7128);
        request.setLon(-74.0060);
        request.setNetworkType(usdot.v2x.app.api.models.etx.NetworkType.NON_VZ);

        RegistrationResponse registrationResponse = new RegistrationResponse();
        registrationResponse.setDeviceId("test-device-id");

        ConnectionResponse connectionResponse = new ConnectionResponse();

        when(registrationApi.clientRegistrationRetryPending(any(RegistrationPostRequest.class), anyInt(), anyInt()))
                .thenReturn(reactor.core.publisher.Mono.just(registrationResponse));
        when(registrationApi.clientConnectionPost(any(ConnectionPostRequest.class)))
                .thenReturn(reactor.core.publisher.Mono.just(connectionResponse));

        // Act & Assert
        StepVerifier.create(controller.postRegistrationConnection(5, 15, request))
                .expectNextMatches(response -> response != null)
                .verifyComplete();

        verify(registrationApi).clientRegistrationRetryPending(any(RegistrationPostRequest.class), eq(5), eq(15));
        verify(registrationApi).clientConnectionPost(any(ConnectionPostRequest.class));
    }

    @Test
    void testCleanupVendorRegistrations_Success() {
        // Arrange
        String vendorId = "testvendor";
        List<String> expectedDeviceIds = Arrays.asList("device1", "device2");
        when(registrationLogService.cleanupOldRegistrationsByVendor(vendorId)).thenReturn(expectedDeviceIds);

        // Act
        ResponseEntity<List<String>> result = controller.cleanupVendorRegistrations(vendorId);

        // Assert
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(expectedDeviceIds, result.getBody());
        verify(registrationLogService).cleanupOldRegistrationsByVendor(vendorId);
    }

    @Test
    void testCleanupVendorRegistrations_Exception() {
        // Arrange
        String vendorId = "testvendor";
        doThrow(new RuntimeException("Database error")).when(registrationLogService)
                .cleanupOldRegistrationsByVendor(vendorId);

        // Act
        ResponseEntity<List<String>> result = controller.cleanupVendorRegistrations(vendorId);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertNull(result.getBody());
    }

    @Test
    void testGetAcls_Success() {
        // Arrange
        Object expectedResponse = new Object();
        when(registrationApi.getDeviceRoles())
                .thenReturn(reactor.core.publisher.Mono.just(expectedResponse));

        // Act
        reactor.core.publisher.Mono<Object> result = controller.getAcls();

        // Assert
        StepVerifier.create(result)
                .expectNext(expectedResponse)
                .verifyComplete();
        verify(registrationApi).getDeviceRoles();
    }

    @Test
    void testGetAcls_Error() {
        // Arrange
        when(registrationApi.getDeviceRoles())
                .thenReturn(reactor.core.publisher.Mono.error(new RuntimeException("API error")));

        // Act & Assert
        StepVerifier.create(controller.getAcls())
                .expectError(RuntimeException.class)
                .verify();
        verify(registrationApi).getDeviceRoles();
    }

    private RegistrationLog createRegistrationLog(String deviceId) {
        RegistrationLog log = new RegistrationLog();
        log.setDeviceId(deviceId);
        log.setClientType(RegistrationClientType.Vehicle);
        log.setClientSubtype(RegistrationClientSubType.PassengerCar);
        log.setVendorId("test-vendor");
        log.setRequestedBy("testuser");
        log.setRegistrationCount(1);
        log.setIsActive(true);
        return log;
    }
}
