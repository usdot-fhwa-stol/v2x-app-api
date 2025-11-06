package usdot.v2x.app.api.etx.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import usdot.v2x.app.api.models.etx.configuration.ConfigurationClearGeofence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeofenceCleanupServiceTest {

    @Mock
    private ConfigurationApi configurationApi;

    private ConfigurationCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        cleanupService = new ConfigurationCleanupService(configurationApi);
    }

    @Test
    void testClearInactiveTimGeofences_ShouldCallConfigurationApi() {
        // Arrange
        List<String> expectedClearedIds = Arrays.asList("geofence-1", "geofence-2");
        ResponseEntity<List<String>> mockResponse = ResponseEntity.ok(expectedClearedIds);

        try {
            when(configurationApi.clearGeofences(any(ConfigurationClearGeofence.class)))
                    .thenReturn(mockResponse);
        } catch (JsonProcessingException e) {
            fail("Unexpected exception during test setup: " + e.getMessage());
        }

        // Act
        cleanupService.clearInactiveTimGeofences();

        // Assert
        ArgumentCaptor<ConfigurationClearGeofence> requestCaptor = ArgumentCaptor
                .forClass(ConfigurationClearGeofence.class);
        try {
            verify(configurationApi, times(1)).clearGeofences(requestCaptor.capture());
        } catch (JsonProcessingException e) {
            fail("Unexpected exception during verification: " + e.getMessage());
        }

        ConfigurationClearGeofence capturedRequest = requestCaptor.getValue();
        assertTrue(capturedRequest.isClearTimOnly(), "Request should be set to clear TIM only");
    }

    @Test
    void testClearInactiveTimGeofences_WhenNoGeofencesCleared_ShouldHandleEmptyResponse() {
        // Arrange
        ResponseEntity<List<String>> mockResponse = ResponseEntity.ok(Arrays.asList());

        try {
            when(configurationApi.clearGeofences(any(ConfigurationClearGeofence.class)))
                    .thenReturn(mockResponse);
        } catch (JsonProcessingException e) {
            fail("Unexpected exception during test setup: " + e.getMessage());
        }

        // Act
        cleanupService.clearInactiveTimGeofences();

        // Assert
        try {
            verify(configurationApi, times(1)).clearGeofences(any(ConfigurationClearGeofence.class));
        } catch (JsonProcessingException e) {
            fail("Unexpected exception during verification: " + e.getMessage());
        }
    }

    @Test
    void testClearInactiveTimGeofences_WhenExceptionOccurs_ShouldHandleError() {
        // Arrange
        try {
            when(configurationApi.clearGeofences(any(ConfigurationClearGeofence.class)))
                    .thenThrow(new RuntimeException("Test error"));
        } catch (JsonProcessingException e) {
            fail("Unexpected exception during test setup: " + e.getMessage());
        }

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> cleanupService.clearInactiveTimGeofences());

        try {
            verify(configurationApi, times(1)).clearGeofences(any(ConfigurationClearGeofence.class));
        } catch (JsonProcessingException e) {
            fail("Unexpected exception during verification: " + e.getMessage());
        }
    }

    @Test
    void testClearInactiveTimGeofences_WhenJsonProcessingExceptionOccurs_ShouldHandleError() {
        // Arrange
        try {
            when(configurationApi.clearGeofences(any(ConfigurationClearGeofence.class)))
                    .thenThrow(new JsonProcessingException("Test JSON error") {
                    });
        } catch (JsonProcessingException e) {
            fail("Unexpected exception during test setup: " + e.getMessage());
        }

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> cleanupService.clearInactiveTimGeofences());

        try {
            verify(configurationApi, times(1)).clearGeofences(any(ConfigurationClearGeofence.class));
        } catch (JsonProcessingException e) {
            fail("Unexpected exception during verification: " + e.getMessage());
        }
    }
}