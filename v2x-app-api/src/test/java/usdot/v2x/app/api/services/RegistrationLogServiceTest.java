package usdot.v2x.app.api.services;

import usdot.v2x.app.api.config.etx.EtxProperties;
import usdot.v2x.app.api.etx.registration.RegistrationApi;
import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.etx.ErrorResponseException;
import usdot.v2x.app.api.models.etx.registration.*;
import usdot.v2x.app.api.models.UserLimits;
import usdot.v2x.app.api.models.VendorLimits;
import usdot.v2x.app.api.repositories.RegistrationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RegistrationLogServiceTest {

    private static final String VENDOR_ID = "test-vendor";
    private static final String USER = "testuser";

    @Mock
    private RegistrationLogRepository registrationLogRepository;

    @Mock
    private RegistrationApi registrationApi;

    @Mock
    private EtxProperties etxProperties;

    @Mock
    private UserLimitsService userLimitsService;

    @Mock
    private VendorLimitsService vendorLimitsService;

    @Mock
    private RegistrationEventService registrationEventService;

    @InjectMocks
    private RegistrationLogService registrationLogService;

    private EtxProperties.RegistrationLimits registrationLimits;

    @BeforeEach
    void setUp() {
        registrationLimits = new EtxProperties.RegistrationLimits();
        EtxProperties.VendorConfig vendorConfig = new EtxProperties.VendorConfig();
        vendorConfig.setId(VENDOR_ID);
        vendorConfig.setLimit(50);
        vendorConfig.setUserLimit(5);
        registrationLimits.setVendors(Collections.singletonList(vendorConfig));
        lenient().when(etxProperties.getRegistration()).thenReturn(registrationLimits);

        UserLimits userLimits = new UserLimits();
        userLimits.setMaxRegistrations(5);
        lenient().when(userLimitsService.getUserLimits(USER, VENDOR_ID, 5)).thenReturn(userLimits);

        VendorLimits vendorLimits = new VendorLimits();
        vendorLimits.setMaxRegistrations(50);
        lenient().when(vendorLimitsService.getVendorLimits(VENDOR_ID, 50)).thenReturn(vendorLimits);

        lenient().when(registrationApi.deleteRegistrations(anyList(), anyString())).thenReturn(Mono.empty());
    }

    // --- logRegistration ---

    @Test
    void testLogRegistration_NewDeviceSavesRowAndRecordsEvent() {
        RegistrationResponse response = createRegistrationResponse();
        when(registrationLogRepository.countActiveRegistrationsByUserAndVendor(USER, VENDOR_ID)).thenReturn(3L);
        when(registrationLogRepository.findByDeviceIds(List.of("test-device-id"))).thenReturn(List.of());

        registrationLogService.logRegistration(response, RegistrationClientType.Vehicle,
                RegistrationClientSubType.PassengerCar, VENDOR_ID, USER);

        ArgumentCaptor<RegistrationLog> logCaptor = ArgumentCaptor.forClass(RegistrationLog.class);
        verify(registrationLogRepository).save(logCaptor.capture());
        RegistrationLog savedLog = logCaptor.getValue();
        assertEquals("test-device-id", savedLog.getDeviceId());
        assertEquals(USER, savedLog.getRequestedBy());
        assertEquals(RegistrationClientType.Vehicle, savedLog.getClientType());
        assertEquals(RegistrationClientSubType.PassengerCar, savedLog.getClientSubtype());
        assertEquals(VENDOR_ID, savedLog.getVendorId());
        assertEquals(4, savedLog.getRegistrationCount());
        assertTrue(savedLog.getIsActive());
        verify(registrationEventService).record(eq(RegistrationEventType.REGISTER), eq("test-device-id"),
                eq(VENDOR_ID), eq(USER), anyString(), isNull(), anyString());
    }

    @Test
    void testLogRegistration_DoesNotEvict() {
        RegistrationResponse response = createRegistrationResponse();
        when(registrationLogRepository.countActiveRegistrationsByUserAndVendor(USER, VENDOR_ID)).thenReturn(5L);
        when(registrationLogRepository.findByDeviceIds(anyList())).thenReturn(List.of());

        registrationLogService.logRegistration(response, RegistrationClientType.Vehicle,
                RegistrationClientSubType.PassengerCar, VENDOR_ID, USER);

        // Capacity is enforced only by ensureCapacityForRegistration
        verify(registrationLogRepository, never()).findOldestRegistrationsByUserAndVendor(anyString(), anyString());
        verify(registrationLogRepository, never()).findOldestTotalRegistrationsByVendor(anyString());
        verify(registrationApi, never()).deleteRegistrations(anyList(), anyString());
    }

    @Test
    void testLogRegistration_AlreadyTrackedDeviceUpdatesExistingRow() {
        RegistrationResponse response = createRegistrationResponse();
        RegistrationLog existing = createRegistration("test-device-id", Instant.now().minusSeconds(7200));
        existing.setId(42L);
        existing.setRegistrationCount(2);
        when(registrationLogRepository.countActiveRegistrationsByUserAndVendor(USER, VENDOR_ID)).thenReturn(3L);
        when(registrationLogRepository.findByDeviceIds(List.of("test-device-id"))).thenReturn(List.of(existing));

        registrationLogService.logRegistration(response, RegistrationClientType.Software,
                RegistrationClientSubType.Application, VENDOR_ID, USER);

        ArgumentCaptor<RegistrationLog> logCaptor = ArgumentCaptor.forClass(RegistrationLog.class);
        verify(registrationLogRepository).save(logCaptor.capture());
        assertSame(existing, logCaptor.getValue());
        assertEquals(42L, logCaptor.getValue().getId());
        assertEquals(2, logCaptor.getValue().getRegistrationCount());
        assertEquals(RegistrationClientType.Software, logCaptor.getValue().getClientType());
        assertEquals(Instant.parse(response.getCertificate().getExpirationTime()),
                logCaptor.getValue().getExpiresAt());
    }

    // --- logRegistrationUpdate ---

    @Test
    void testLogRegistrationUpdate_RefreshesExpirationWithoutEviction() {
        RegistrationResponse response = createRegistrationResponse();
        RegistrationLog existing = createRegistration("test-device-id", Instant.now().minusSeconds(7200));
        existing.setExpiresAt(Instant.now().minusSeconds(60));
        when(registrationLogRepository.findByDeviceIds(List.of("test-device-id"))).thenReturn(List.of(existing));

        registrationLogService.logRegistrationUpdate(response, "test-device-id", VENDOR_ID, USER);

        assertEquals(Instant.parse(response.getCertificate().getExpirationTime()), existing.getExpiresAt());
        verify(registrationLogRepository).saveAll(List.of(existing));
        verify(registrationEventService).record(eq(RegistrationEventType.PUT), eq("test-device-id"), eq(VENDOR_ID),
                eq(USER), anyString(), isNull(), anyString());
        verify(registrationApi, never()).deleteRegistrations(anyList(), anyString());
    }

    // --- ensureCapacityForRegistration ---

    @Test
    void testEnsureCapacity_UnderLimitsDoesNotEvict() {
        when(registrationApi.queryActiveClients(VENDOR_ID)).thenReturn(Mono.just(List.of()));
        when(registrationLogRepository.countActiveRegistrationsByUserAndVendor(USER, VENDOR_ID)).thenReturn(3L);
        when(registrationLogRepository.countTotalActiveRegistrationsByVendor(VENDOR_ID)).thenReturn(10L);

        registrationLogService.ensureCapacityForRegistration(VENDOR_ID, USER);

        verify(registrationApi, never()).deleteRegistrations(anyList(), anyString());
    }

    @Test
    void testEnsureCapacity_UserLimitReachedEvictsOldest() {
        when(registrationApi.queryActiveClients(VENDOR_ID)).thenReturn(Mono.just(List.of()));
        when(registrationLogRepository.countActiveRegistrationsByUserAndVendor(USER, VENDOR_ID)).thenReturn(5L);
        when(registrationLogRepository.countTotalActiveRegistrationsByVendor(VENDOR_ID)).thenReturn(10L);
        List<RegistrationLog> oldRegistrations = createOldRegistrations(5);
        when(registrationLogRepository.findOldestRegistrationsByUserAndVendor(USER, VENDOR_ID))
                .thenReturn(oldRegistrations);

        registrationLogService.ensureCapacityForRegistration(VENDOR_ID, USER);

        // 5 active at a limit of 5: remove exactly one to make room
        verify(registrationApi).deleteRegistrations(List.of("device0"), VENDOR_ID);
        verify(registrationEventService).recordForRegistrations(eq(RegistrationEventType.EVICT_USER_LIMIT),
                eq(List.of(oldRegistrations.get(0))), anyString(), isNull());
    }

    @Test
    void testEnsureCapacity_VendorLimitReachedEvictsOnlyOne() {
        when(registrationApi.queryActiveClients(VENDOR_ID)).thenReturn(Mono.just(List.of()));
        when(registrationLogRepository.countActiveRegistrationsByUserAndVendor(USER, VENDOR_ID)).thenReturn(1L);
        when(registrationLogRepository.countTotalActiveRegistrationsByVendor(VENDOR_ID)).thenReturn(50L);
        List<RegistrationLog> vendorRegistrations = createOldRegistrations(50);
        when(registrationLogRepository.findOldestTotalRegistrationsByVendor(VENDOR_ID))
                .thenReturn(vendorRegistrations);

        registrationLogService.ensureCapacityForRegistration(VENDOR_ID, USER);

        verify(registrationApi).deleteRegistrations(List.of("device0"), VENDOR_ID);
        assertFalse(vendorRegistrations.get(0).getIsActive());
        assertTrue(vendorRegistrations.subList(1, 50).stream().allMatch(RegistrationLog::getIsActive));
    }

    @Test
    void testEnsureCapacity_ReconcileFailureFallsBackToLocalCounts() {
        when(registrationApi.queryActiveClients(VENDOR_ID))
                .thenReturn(Mono.error(new RuntimeException("ETX unavailable")));
        when(registrationLogRepository.countActiveRegistrationsByUserAndVendor(USER, VENDOR_ID)).thenReturn(1L);
        when(registrationLogRepository.countTotalActiveRegistrationsByVendor(VENDOR_ID)).thenReturn(1L);

        assertDoesNotThrow(() -> registrationLogService.ensureCapacityForRegistration(VENDOR_ID, USER));

        verify(registrationLogRepository).countTotalActiveRegistrationsByVendor(VENDOR_ID);
    }

    @Test
    void testEnsureCapacity_ReconcileDisabledSkipsEtxQuery() {
        registrationLimits.getCleanup().setReconcileOnRegister(false);
        when(registrationLogRepository.countActiveRegistrationsByUserAndVendor(USER, VENDOR_ID)).thenReturn(1L);
        when(registrationLogRepository.countTotalActiveRegistrationsByVendor(VENDOR_ID)).thenReturn(1L);

        registrationLogService.ensureCapacityForRegistration(VENDOR_ID, USER);

        verify(registrationApi, never()).queryActiveClients(anyString());
    }

    // --- cleanup ---

    @Test
    void testCleanupOldRegistrations() {
        List<RegistrationLog> oldRegistrations = createOldRegistrations(7);
        when(registrationLogRepository.findOldestRegistrationsByUserAndVendor(USER, VENDOR_ID))
                .thenReturn(oldRegistrations);

        List<String> result = registrationLogService.cleanupOldRegistrations(USER, VENDOR_ID);

        assertEquals(List.of("device0", "device1", "device2"), result); // 7 - 5 + 1 = 3
        verify(registrationApi).deleteRegistrations(result, VENDOR_ID);
        verify(registrationLogRepository).saveAll(anyList());
    }

    @Test
    void testCleanupOldRegistrationsByVendor_RemovesOnlyExcess() {
        List<RegistrationLog> oldVendorRegistrations = createOldRegistrations(55);
        when(registrationLogRepository.findOldestTotalRegistrationsByVendor(VENDOR_ID))
                .thenReturn(oldVendorRegistrations);

        List<String> result = registrationLogService.cleanupOldRegistrationsByVendor(VENDOR_ID);

        assertEquals(6, result.size()); // 55 - 50 + 1 = 6, not all 55
        assertEquals("device0", result.get(0));
        verify(registrationApi).deleteRegistrations(result, VENDOR_ID);
        verify(registrationEventService).recordForRegistrations(eq(RegistrationEventType.EVICT_VENDOR_LIMIT),
                anyList(), anyString(), isNull());
    }

    @Test
    void testCleanupOldRegistrationsByVendor_UnderLimitRemovesNothing() {
        when(registrationLogRepository.findOldestTotalRegistrationsByVendor(VENDOR_ID))
                .thenReturn(createOldRegistrations(10));

        List<String> result = registrationLogService.cleanupOldRegistrationsByVendor(VENDOR_ID);

        assertTrue(result.isEmpty());
        verify(registrationApi, never()).deleteRegistrations(anyList(), anyString());
    }

    @Test
    void testEviction_EtxDeleteFailureKeepsRowsActiveAndRecordsFailure() {
        List<RegistrationLog> oldRegistrations = createOldRegistrations(6);
        when(registrationLogRepository.findOldestRegistrationsByUserAndVendor(USER, VENDOR_ID))
                .thenReturn(oldRegistrations);
        when(registrationApi.deleteRegistrations(anyList(), anyString())).thenReturn(Mono.error(
                new ErrorResponseException(new ErrorResponse("error", "boom"), HttpStatus.BAD_GATEWAY)));

        List<String> result = registrationLogService.cleanupOldRegistrations(USER, VENDOR_ID);

        assertTrue(result.isEmpty());
        assertTrue(oldRegistrations.stream().allMatch(RegistrationLog::getIsActive));
        verify(registrationLogRepository, never()).saveAll(anyList());
        verify(registrationEventService).recordForRegistrations(eq(RegistrationEventType.DELETE_FAILED), anyList(),
                anyString(), eq(502));
    }

    @Test
    void testCleanupExpiredRegistrations_VerifiesWithEtxBeforeDeleting() {
        Instant now = Instant.now();
        RegistrationLog renewed = createRegistration("renewed", now.minusSeconds(86400));
        RegistrationLog gone = createRegistration("gone", now.minusSeconds(86400));
        RegistrationLog expired = createRegistration("expired", now.minusSeconds(86400));
        List.of(renewed, gone, expired).forEach(r -> r.setExpiresAt(now.minusSeconds(60)));
        when(registrationLogRepository.findExpiredRegistrations(any(Instant.class)))
                .thenReturn(List.of(renewed, gone, expired));

        Instant renewedExpiry = now.plusSeconds(3600);
        when(registrationApi.checkRegistration("renewed", VENDOR_ID))
                .thenReturn(Mono.just(checkResponse("renewed", renewedExpiry.toString())));
        when(registrationApi.checkRegistration("gone", VENDOR_ID)).thenReturn(Mono.error(
                new ErrorResponseException(new ErrorResponse("Device not found", "gone"), HttpStatus.NOT_FOUND)));
        when(registrationApi.checkRegistration("expired", VENDOR_ID))
                .thenReturn(Mono.just(checkResponse("expired", now.minusSeconds(60).toString())));

        List<String> result = registrationLogService.cleanupExpiredRegistrations();

        assertEquals(Set.of("gone", "expired"), new HashSet<>(result));
        // Renewed device: local expiry refreshed, never deleted
        assertTrue(renewed.getIsActive());
        assertEquals(renewedExpiry, renewed.getExpiresAt());
        // Device ETX no longer knows: deactivated without a delete call
        assertFalse(gone.getIsActive());
        // Still expired: deleted from ETX first, then deactivated
        assertFalse(expired.getIsActive());
        InOrder inOrder = inOrder(registrationApi, registrationLogRepository);
        inOrder.verify(registrationApi).deleteRegistrations(List.of("expired"), VENDOR_ID);
        inOrder.verify(registrationLogRepository).saveAll(argThat(list -> {
            List<RegistrationLog> rows = new ArrayList<>();
            list.forEach(rows::add);
            return rows.contains(expired);
        }));
        verify(registrationApi, times(1)).deleteRegistrations(anyList(), anyString());
    }

    @Test
    void testCleanupExpiredRegistrations_EtxCheckErrorSkipsDevice() {
        RegistrationLog expired = createRegistration("expired", Instant.now().minusSeconds(86400));
        when(registrationLogRepository.findExpiredRegistrations(any(Instant.class))).thenReturn(List.of(expired));
        when(registrationApi.checkRegistration("expired", VENDOR_ID)).thenReturn(Mono.error(
                new ErrorResponseException(new ErrorResponse("error", "down"), HttpStatus.SERVICE_UNAVAILABLE)));

        List<String> result = registrationLogService.cleanupExpiredRegistrations();

        assertTrue(result.isEmpty());
        assertTrue(expired.getIsActive());
        verify(registrationApi, never()).deleteRegistrations(anyList(), anyString());
    }

    // --- reconcile ---

    @Test
    void testReconcileWithEtx_DeactivatesMissingAndReportsUntracked() {
        RegistrationLog tracked = createRegistration("tracked", Instant.now().minusSeconds(3600));
        RegistrationLog missing = createRegistration("missing", Instant.now().minusSeconds(3600));
        RegistrationLog justRegistered = createRegistration("just-registered", Instant.now());
        when(registrationLogRepository.findActiveByVendor(VENDOR_ID))
                .thenReturn(List.of(tracked, missing, justRegistered));
        when(registrationApi.queryActiveClients(VENDOR_ID))
                .thenReturn(Mono.just(List.of(etxClient("tracked"), etxClient("untracked"))));
        when(registrationEventService.hasEvent("untracked", RegistrationEventType.UNTRACKED_IN_ETX)).thenReturn(false);

        ReconcileResult result = registrationLogService.reconcileWithEtx(VENDOR_ID);

        assertEquals(2, result.getEtxCount());
        assertEquals(List.of("missing"), result.getDeactivated());
        assertEquals(List.of("untracked"), result.getUntrackedInEtx());
        assertFalse(missing.getIsActive());
        assertTrue(tracked.getIsActive());
        // Inside the grace period, so not treated as missing
        assertTrue(justRegistered.getIsActive());
        verify(registrationEventService).recordForRegistrations(eq(RegistrationEventType.RECONCILED_MISSING),
                eq(List.of(missing)), anyString(), isNull());
        verify(registrationEventService).record(eq(RegistrationEventType.UNTRACKED_IN_ETX), eq("untracked"),
                eq(VENDOR_ID), isNull(), anyString());
        verify(registrationApi, never()).deleteRegistrations(anyList(), anyString());
    }

    @Test
    void testReconcileWithEtx_UntrackedEventRecordedOnlyOnce() {
        when(registrationLogRepository.findActiveByVendor(VENDOR_ID)).thenReturn(List.of());
        when(registrationApi.queryActiveClients(VENDOR_ID)).thenReturn(Mono.just(List.of(etxClient("untracked"))));
        when(registrationEventService.hasEvent("untracked", RegistrationEventType.UNTRACKED_IN_ETX)).thenReturn(true);

        ReconcileResult result = registrationLogService.reconcileWithEtx(VENDOR_ID);

        assertEquals(List.of("untracked"), result.getUntrackedInEtx());
        verify(registrationEventService, never()).record(eq(RegistrationEventType.UNTRACKED_IN_ETX), anyString(),
                anyString(), any(), anyString());
    }

    // --- active registrations view ---

    @Test
    void testGetActiveRegistrationsView_MergesEtxAndLocal() {
        RegistrationLog tracked = createRegistration("tracked", Instant.now().minusSeconds(3600));
        tracked.setLastConnected(Instant.now().minusSeconds(60));
        RegistrationLog stale = createRegistration("stale", Instant.now().minusSeconds(3600));
        when(registrationLogRepository.findActiveByVendor(VENDOR_ID)).thenReturn(List.of(tracked, stale));
        when(registrationApi.queryActiveClients(VENDOR_ID))
                .thenReturn(Mono.just(List.of(etxClient("tracked"), etxClient("untracked"))));

        StepVerifier.create(registrationLogService.getActiveRegistrationsView(VENDOR_ID))
                .assertNext(response -> {
                    assertEquals(VENDOR_ID, response.getVendorId());
                    assertEquals(2, response.getEtxCount());
                    assertEquals(2, response.getLocalActiveCount());
                    assertEquals(List.of("untracked"), response.getUntrackedInEtx());
                    assertEquals(List.of("stale"), response.getStaleLocal());

                    ActiveRegistrationView trackedView = response.getItems().get(0);
                    assertEquals("tracked", trackedView.getDeviceId());
                    assertTrue(trackedView.isTrackedLocally());
                    assertEquals(USER, trackedView.getRequestedBy());
                    assertEquals(tracked.getLastConnected(), trackedView.getLastConnected());
                    assertEquals(List.of("tsp-sfo-1-eks-prod-edge-us-west-2-imp-prod"), trackedView.getMecIds());

                    ActiveRegistrationView untrackedView = response.getItems().get(1);
                    assertFalse(untrackedView.isTrackedLocally());
                    assertNull(untrackedView.getRequestedBy());
                })
                .verifyComplete();
    }

    // --- simple accessors ---

    @Test
    void testGetActiveRegistrations() {
        List<RegistrationLog> expectedLogs = createOldRegistrations(3);
        when(registrationLogRepository.findActiveRegistrationsByUser(USER)).thenReturn(expectedLogs);

        assertEquals(expectedLogs, registrationLogService.getActiveRegistrations(USER));
    }

    @Test
    void testGetRegistrationCount() {
        when(registrationLogRepository.countActiveRegistrationsByUser(USER)).thenReturn(5L);

        assertEquals(5L, registrationLogService.getRegistrationCount(USER));
    }

    private RegistrationResponse createRegistrationResponse() {
        RegistrationResponse response = new RegistrationResponse();
        response.setDeviceId("test-device-id");

        Certificate certificate = new Certificate();
        certificate.setExpirationTime(Instant.now().plusSeconds(3600).toString());
        response.setCertificate(certificate);

        return response;
    }

    private RegistrationCheckResponse checkResponse(String deviceId, String expirationTime) {
        RegistrationCheckResponse response = new RegistrationCheckResponse();
        response.setDeviceId(deviceId);
        response.setVendorId(VENDOR_ID);
        Certificate certificate = new Certificate();
        certificate.setExpirationTime(expirationTime);
        response.setCertificate(certificate);
        return response;
    }

    private EtxClientRecord etxClient(String deviceId) {
        EtxClientRecord record = new EtxClientRecord();
        record.setDeviceId(deviceId);
        record.setClientType(RegistrationClientType.Software);
        record.setClientSubtype(RegistrationClientSubType.Application);
        record.setMecIds(List.of("tsp-sfo-1-eks-prod-edge-us-west-2-imp-prod"));
        return record;
    }

    private RegistrationLog createRegistration(String deviceId, Instant createdAt) {
        RegistrationLog log = new RegistrationLog();
        log.setDeviceId(deviceId);
        log.setClientType(RegistrationClientType.Vehicle);
        log.setClientSubtype(RegistrationClientSubType.PassengerCar);
        log.setVendorId(VENDOR_ID);
        log.setRequestedBy(USER);
        log.setRegistrationCount(1);
        log.setCreatedAt(createdAt);
        log.setExpiresAt(Instant.now().plusSeconds(3600));
        log.setIsActive(true);
        return log;
    }

    /** Registrations ordered oldest first, as the repository returns them */
    private List<RegistrationLog> createOldRegistrations(int count) {
        List<RegistrationLog> logs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            logs.add(createRegistration("device" + i, Instant.now().minusSeconds(3600L * (count - i))));
        }
        return logs;
    }
}
