package usdot.v2x.app.api.etx.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;

import usdot.v2x.app.api.config.etx.EtxProperties;
import usdot.v2x.app.api.models.etx.configuration.ConfigurationClearGeofence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeofenceCleanupServiceTest {

    @Mock
    private ConfigurationApi configurationApi;

    @Mock
    private EtxProperties etxProperties;

    private ConfigurationCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(etxProperties.getEnabled()).thenReturn(true);
        cleanupService = new ConfigurationCleanupService(configurationApi, etxProperties);
    }

    @Test
    void testClearInactiveTimGeofences_ShouldCallConfigurationApi() {
        // Arrange
        List<String> expectedClearedIds = Arrays.asList("geofence-1", "geofence-2");
        ResponseEntity<List<String>> mockResponse = ResponseEntity.ok(expectedClearedIds);

        when(configurationApi.clearGeofences(any(ConfigurationClearGeofence.class)))
                .thenReturn(Mono.just(mockResponse));

        // Act
        cleanupService.clearInactiveTimGeofences();

        // Assert
        ArgumentCaptor<ConfigurationClearGeofence> requestCaptor = ArgumentCaptor
                .forClass(ConfigurationClearGeofence.class);
        verify(configurationApi, times(1)).clearGeofences(requestCaptor.capture());

        ConfigurationClearGeofence capturedRequest = requestCaptor.getValue();
        assertTrue(capturedRequest.isClearTimOnly(), "Request should be set to clear TIM only");
    }

    @Test
    void testClearInactiveTimGeofences_WhenNoGeofencesCleared_ShouldHandleEmptyResponse() {
        // Arrange
        ResponseEntity<List<String>> mockResponse = ResponseEntity.ok(Arrays.asList());

        when(configurationApi.clearGeofences(any(ConfigurationClearGeofence.class)))
                .thenReturn(Mono.just(mockResponse));

        // Act
        cleanupService.clearInactiveTimGeofences();

        // Assert
        verify(configurationApi, times(1)).clearGeofences(any(ConfigurationClearGeofence.class));
    }

    @Test
    void testClearInactiveTimGeofences_WhenExceptionOccurs_ShouldHandleError() {
        // Arrange
        when(configurationApi.clearGeofences(any(ConfigurationClearGeofence.class)))
                .thenReturn(Mono.error(new RuntimeException("Test error")));

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> cleanupService.clearInactiveTimGeofences());

        verify(configurationApi, times(1)).clearGeofences(any(ConfigurationClearGeofence.class));
    }

    @Test
    void testClearInactiveTimGeofences_WhenJsonProcessingExceptionOccurs_ShouldHandleError() {
        // Arrange
        when(configurationApi.clearGeofences(any(ConfigurationClearGeofence.class)))
                .thenReturn(Mono.error(new JsonProcessingException("Test JSON error") {
                }));

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> cleanupService.clearInactiveTimGeofences());

        verify(configurationApi, times(1)).clearGeofences(any(ConfigurationClearGeofence.class));
    }

    @Test
    void testClearInactiveTimGeofences_WhenEtxDisabled_ShouldSkipCleanup() {
        // Arrange
        when(etxProperties.getEnabled()).thenReturn(false);
        ConfigurationCleanupService disabledCleanupService = new ConfigurationCleanupService(configurationApi,
                etxProperties);

        // Act
        disabledCleanupService.clearInactiveTimGeofences();

        // Assert
        verify(configurationApi, never()).clearGeofences(any(ConfigurationClearGeofence.class));
    }
}