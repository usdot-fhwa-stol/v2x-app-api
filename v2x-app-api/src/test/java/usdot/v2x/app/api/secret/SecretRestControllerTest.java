package usdot.v2x.app.api.secret;

import usdot.v2x.app.api.models.dto.SecretResponse;
import usdot.v2x.app.api.models.dto.S3Config;
import usdot.v2x.app.api.services.SecretService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecretRestController Tests")
class SecretRestControllerTest {

    @Mock
    private SecretService secretService;

    @InjectMocks
    private SecretRestController secretRestController;

    @Test
    @DisplayName("Should successfully get secret configuration")
    void getSecretConfig_ShouldReturnSuccess() {
        // Given - using values from application-test.yml
        S3Config s3Config = new S3Config("key", "key", "name", "region", "destination");
        SecretResponse secretResponse = new SecretResponse(
                "token",
                s3Config,
                "mapbox_access_token",
                "noaa-geomag-api-token");

        when(secretService.getSecretConfig()).thenReturn(Mono.just(secretResponse));

        // When
        Mono<SecretResponse> result = secretRestController.getSecretConfig();

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals("token", response.getIssScmsToken());
                    assertNotNull(response.getS3());
                    assertEquals("key", response.getS3().getS3AccessKey());
                    assertEquals("key", response.getS3().getS3SecretKey());
                    assertEquals("name", response.getS3().getS3BucketName());
                    assertEquals("region", response.getS3().getS3Region());
                    assertEquals("destination", response.getS3().getS3Destination());
                    assertEquals("mapbox_access_token", response.getMapboxAccessToken());
                    assertEquals("noaa-geomag-api-token", response.getNoaaGeomagApiToken());
                })
                .verifyComplete();

        verify(secretService).getSecretConfig();
    }

    @Test
    @DisplayName("Should handle service error when getting secret configuration")
    void getSecretConfig_WhenServiceThrowsException_ShouldReturnError() {
        // Given
        when(secretService.getSecretConfig()).thenReturn(Mono.error(new RuntimeException("Service error")));

        // When
        Mono<SecretResponse> result = secretRestController.getSecretConfig();

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(secretService).getSecretConfig();
    }
}
