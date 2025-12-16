package usdot.v2x.app.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import usdot.v2x.app.api.models.geofence.TimConfigurationResponse;
import usdot.v2x.app.api.models.geofence.TimOverlay;
import usdot.v2x.app.api.models.geofence.TimPhrase;
import usdot.v2x.app.api.services.TimConfigurationServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimConfigurationServiceImpl Tests")
class TimConfigurationServiceImplTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TimConfigurationServiceImpl timConfigurationService;

    private String testConfigFilePath;
    private String testIconsDirectory;
    private TimConfigurationResponse testConfigResponse;

    @BeforeEach
    void setUp() throws IOException {
        // Set up test paths using test resources
        testConfigFilePath = "src/test/resources/tim-test-data/tim-config.json";
        testIconsDirectory = "src/test/resources/tim-test-data";

        // Set the field values in the service
        ReflectionTestUtils.setField(timConfigurationService, "timConfigFilePath", testConfigFilePath);
        ReflectionTestUtils.setField(timConfigurationService, "timIconsDirectory", testIconsDirectory);

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

        // Set up reflection for private fields
        ReflectionTestUtils.setField(timConfigurationService, "timConfigFilePath", testConfigFilePath);
        ReflectionTestUtils.setField(timConfigurationService, "timIconsDirectory", testIconsDirectory);
    }

    @Test
    @DisplayName("Should successfully get TIM configuration")
    void testGetTimConfiguration_Success() throws Exception {
        // Given
        String testJson = "{\"version\":0.1,\"tims\":[{\"name\":\"test-phrase\",\"type\":\"advisory\",\"codes\":[\"123\",\"456\"],\"graphic\":\"test.png\",\"overlays\":[{\"majorFontSize\":24,\"minorFontSize\":16,\"xPos\":0,\"yPos\":0,\"align\":\"center\"}]}]}";
        Files.write(Paths.get(testConfigFilePath), testJson.getBytes());

        when(objectMapper.readValue(anyString(), eq(TimConfigurationResponse.class)))
                .thenReturn(testConfigResponse);

        // When
        Mono<TimConfigurationResponse> result = timConfigurationService.getTimConfiguration();

        // Then
        StepVerifier.create(result)
                .expectNext(testConfigResponse)
                .verifyComplete();

        verify(objectMapper).readValue(anyString(), eq(TimConfigurationResponse.class));
    }

    @Test
    @DisplayName("Should handle file not found error")
    void testGetTimConfiguration_FileNotFound() {
        // Given - file doesn't exist
        String nonExistentPath = "/tmp/non-existent-config.json";
        ReflectionTestUtils.setField(timConfigurationService, "timConfigFilePath", nonExistentPath);

        // When
        Mono<TimConfigurationResponse> result = timConfigurationService.getTimConfiguration();

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("Should handle JSON parsing error")
    void testGetTimConfiguration_JsonParsingError() throws Exception {
        // Given - use existing test file but mock the ObjectMapper to throw an error
        when(objectMapper.readValue(anyString(), eq(TimConfigurationResponse.class)))
                .thenThrow(new RuntimeException("JSON parsing error"));

        // When
        Mono<TimConfigurationResponse> result = timConfigurationService.getTimConfiguration();

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(objectMapper).readValue(anyString(), eq(TimConfigurationResponse.class));
    }

    @Test
    @DisplayName("Should successfully create TIM icons TAR.GZ from versioned directory")
    void testGetTimIconsTarGz_Success() throws Exception {
        // Given
        String version = "1.0.0";
        String[] iconFiles = { "test.png", "icon1.svg", "icon2.png", "subdir/icon3.jpg" };

        // Create versioned directory structure
        Path versionedDir = Paths.get(testIconsDirectory, "v" + version);
        Files.createDirectories(versionedDir);

        // Create test icon files in versioned directory
        for (String iconFile : iconFiles) {
            Path filePath = versionedDir.resolve(iconFile);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, ("test content for " + iconFile).getBytes());
        }

        // When
        Mono<byte[]> result = timConfigurationService.getTimIconsTarGz(version);

        // Then
        StepVerifier.create(result)
                .assertNext(data -> {
                    // Verify that we get some data back (tar.gz should not be empty)
                    assert data.length > 0;
                    // Verify it starts with gzip magic number (1f 8b)
                    assert data[0] == (byte) 0x1f;
                    assert data[1] == (byte) 0x8b;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle empty versioned icons directory")
    void testGetTimIconsTarGz_EmptyDirectory() throws Exception {
        // Given
        String version = "1.0.0";
        Path versionedDir = Paths.get(testIconsDirectory, "v" + version);
        Files.createDirectories(versionedDir);
        // Directory exists but is empty

        // When
        Mono<byte[]> result = timConfigurationService.getTimIconsTarGz(version);

        // Then
        StepVerifier.create(result)
                .assertNext(data -> {
                    // Should still create a valid tar.gz with just end-of-archive markers
                    assert data.length > 0;
                    assert data[0] == (byte) 0x1f;
                    assert data[1] == (byte) 0x8b;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle non-existent versioned icons directory")
    void testGetTimIconsTarGz_NonExistentDirectory() throws Exception {
        // Given
        String version = "2.0.0"; // Version that doesn't exist
        // Don't create the versioned directory

        // When
        Mono<byte[]> result = timConfigurationService.getTimIconsTarGz(version);

        // Then
        StepVerifier.create(result)
                .assertNext(data -> {
                    // Should still create a valid tar.gz with just end-of-archive markers
                    assert data.length > 0;
                    assert data[0] == (byte) 0x1f;
                    assert data[1] == (byte) 0x8b;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle nested directory structure in versioned directory")
    void testGetTimIconsTarGz_NestedDirectories() throws Exception {
        // Given
        String version = "1.0.0";
        String[] files = {
                "root-icon.png",
                "subdir1/icon1.svg",
                "subdir1/icon2.png",
                "subdir1/nested/icon3.jpg",
                "subdir2/icon4.png"
        };

        // Create versioned directory structure
        Path versionedDir = Paths.get(testIconsDirectory, "v" + version);
        Files.createDirectories(versionedDir);

        // Create nested directory structure with files in versioned directory
        for (String file : files) {
            Path filePath = versionedDir.resolve(file);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, ("content for " + file).getBytes());
        }

        // When
        Mono<byte[]> result = timConfigurationService.getTimIconsTarGz(version);

        // Then
        StepVerifier.create(result)
                .assertNext(data -> {
                    // Should create a valid tar.gz with all files
                    assert data.length > 0;
                    assert data[0] == (byte) 0x1f;
                    assert data[1] == (byte) 0x8b;
                })
                .verifyComplete();
    }

}
