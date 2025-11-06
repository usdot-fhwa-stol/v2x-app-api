package usdot.v2x.app.api.tim;

import usdot.v2x.app.api.models.geofence.TimConfigurationResponse;
import usdot.v2x.app.api.models.geofence.TimOverlay;
import usdot.v2x.app.api.models.geofence.TimPhrase;
import usdot.v2x.app.api.services.TimConfigurationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimConfigurationRestController Tests")
class TimConfigurationRestControllerTest {

    @Mock
    private TimConfigurationService timConfigurationService;

    @InjectMocks
    private TimConfigurationRestController timConfigurationRestController;

    private TimConfigurationResponse testConfigResponse;
    private byte[] testIconsData;

    @BeforeEach
    void setUp() {
        // Set up test configuration response
        TimOverlay overlay = new TimOverlay(24, 16, 0, 0, "center");
        TimPhrase phrase = new TimPhrase(
                "test-phrase",
                "guidance",
                "advisory",
                Arrays.asList("123", "456"),
                "test.png",
                Arrays.asList(overlay));
        testConfigResponse = new TimConfigurationResponse("0.1", Arrays.asList(phrase));

        // Set up test icons data
        testIconsData = "test tar.gz content".getBytes();
    }

    @Test
    @DisplayName("Should successfully get TIM configuration")
    void testGetTimConfiguration_Success() {
        // Given
        when(timConfigurationService.getTimConfiguration())
                .thenReturn(Mono.just(testConfigResponse));

        // When
        Mono<ResponseEntity<TimConfigurationResponse>> result = timConfigurationRestController.getTimConfiguration();

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertNotNull(response.getBody());
                    assertEquals(testConfigResponse.getVersion(), response.getBody().getVersion());
                    assertEquals(testConfigResponse.getTims().size(), response.getBody().getTims().size());
                })
                .verifyComplete();

        verify(timConfigurationService).getTimConfiguration();
    }

    @Test
    @DisplayName("Should handle service error when getting TIM configuration")
    void testGetTimConfiguration_ServiceError() {
        // Given
        when(timConfigurationService.getTimConfiguration())
                .thenReturn(Mono.error(new RuntimeException("Service error")));

        // When
        Mono<ResponseEntity<TimConfigurationResponse>> result = timConfigurationRestController.getTimConfiguration();

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(timConfigurationService).getTimConfiguration();
    }

    @Test
    @DisplayName("Should successfully download TIM icons as TAR.GZ for specific version")
    void testDownloadTimIcons_Success() {
        // Given
        String version = "1.0.0";
        when(timConfigurationService.getTimIconsTarGz(version))
                .thenReturn(Mono.just(testIconsData));

        // When
        Mono<ResponseEntity<byte[]>> result = timConfigurationRestController.downloadTimIcons(version);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertNotNull(response.getBody());
                    assertArrayEquals(testIconsData, response.getBody());

                    // Check headers
                    HttpHeaders headers = response.getHeaders();
                    assertEquals(MediaType.APPLICATION_OCTET_STREAM, headers.getContentType());
                    assertEquals("attachment; filename=\"tim-icons-v" + version + ".tar.gz\"",
                            headers.getContentDisposition().toString());
                    assertEquals(testIconsData.length, headers.getContentLength());
                })
                .verifyComplete();

        verify(timConfigurationService).getTimIconsTarGz(version);
    }

    @Test
    @DisplayName("Should handle service error when downloading TIM icons")
    void testDownloadTimIcons_ServiceError() {
        // Given
        String version = "1.0.0";
        when(timConfigurationService.getTimIconsTarGz(version))
                .thenReturn(Mono.error(new RuntimeException("Service error")));

        // When
        Mono<ResponseEntity<byte[]>> result = timConfigurationRestController.downloadTimIcons(version);

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(timConfigurationService).getTimIconsTarGz(version);
    }

    @Test
    @DisplayName("Should handle empty icons data")
    void testDownloadTimIcons_EmptyData() {
        // Given
        String version = "1.0.0";
        byte[] emptyData = new byte[0];
        when(timConfigurationService.getTimIconsTarGz(version))
                .thenReturn(Mono.just(emptyData));

        // When
        Mono<ResponseEntity<byte[]>> result = timConfigurationRestController.downloadTimIcons(version);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertArrayEquals(emptyData, response.getBody());
                    assertEquals(0, response.getHeaders().getContentLength());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle large icons data")
    void testDownloadTimIcons_LargeData() {
        // Given
        String version = "1.0.0";
        byte[] largeData = new byte[1024 * 1024]; // 1MB
        Arrays.fill(largeData, (byte) 0x42); // Fill with test data
        when(timConfigurationService.getTimIconsTarGz(version))
                .thenReturn(Mono.just(largeData));

        // When
        Mono<ResponseEntity<byte[]>> result = timConfigurationRestController.downloadTimIcons(version);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertArrayEquals(largeData, response.getBody());
                    assertEquals(largeData.length, response.getHeaders().getContentLength());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle different content sizes")
    void testDownloadTimIcons_DifferentContentSizes() {
        // Given
        String version = "1.0.0";
        byte[][] testContents = {
                "small content".getBytes(),
                "medium content with more data".getBytes(),
                new byte[1024 * 100] // 100KB
        };

        // Test each content size
        for (int i = 0; i < testContents.length; i++) {
            byte[] content = testContents[i];
            Arrays.fill(content, (byte) 0x42); // Fill with test data

            when(timConfigurationService.getTimIconsTarGz(version))
                    .thenReturn(Mono.just(content));

            // When
            Mono<ResponseEntity<byte[]>> result = timConfigurationRestController.downloadTimIcons(version);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.getStatusCode());
                        assertArrayEquals(content, response.getBody());

                        // Check filename in Content-Disposition header
                        assertTrue(response.getHeaders().getContentDisposition().toString()
                                .contains("filename=\"tim-icons-v" + version + ".tar.gz\""));
                    })
                    .verifyComplete();
        }
    }
}