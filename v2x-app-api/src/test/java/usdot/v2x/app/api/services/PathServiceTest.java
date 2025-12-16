package usdot.v2x.app.api.services;

import usdot.v2x.app.api.models.Path;
import usdot.v2x.app.api.models.dto.PathRequest;
import usdot.v2x.app.api.models.dto.PathResponse;
import usdot.v2x.app.api.models.dto.PathsResponse;
import usdot.v2x.app.api.models.dto.PathOperationResult;
import usdot.v2x.app.api.repositories.PathRepository;
import usdot.v2x.app.api.services.PathServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PathService Tests")
class PathServiceTest {

    @Mock
    private PathRepository pathRepository;

    @InjectMocks
    private PathServiceImpl pathService;

    private Path testPath;
    private PathRequest testPathRequest;

    @BeforeEach
    void setUp() {
        // Create test path entity
        testPath = new Path();
        testPath.setId(1L);
        testPath.setName("Test Path");
        testPath.setType("Feature");
        testPath.setGeometryType("LineString");
        testPath.setCoordinates(Arrays.asList("[-105.01621,39.57422]", "[-105.01623,39.57424]"));
        testPath.setTimestamps(Arrays.asList(0L, 100L));
        testPath.setIsActive(true);
        testPath.setCreatedAt(Instant.now());
        testPath.setUpdatedAt(Instant.now());
        testPath.setCreatedBy("admin");
        testPath.setUpdatedBy("admin");

        // Create test path request
        PathRequest.PathProperties properties = new PathRequest.PathProperties();
        properties.setName("Test Path");

        PathRequest.PathGeometry geometry = new PathRequest.PathGeometry();
        geometry.setType("LineString");
        geometry.setCoordinates(Arrays.asList(
                Arrays.asList(-105.01621, 39.57422),
                Arrays.asList(-105.01623, 39.57424)));

        testPathRequest = new PathRequest();
        testPathRequest.setName("Test Path");
        testPathRequest.setType("Feature");
        testPathRequest.setProperties(properties);
        testPathRequest.setGeometry(geometry);
        testPathRequest.setTimestamps(Arrays.asList(0L, 100L));
    }

    @Test
    @DisplayName("Should successfully get all paths")
    void testGetAllPaths() {
        // Given
        List<Path> paths = Arrays.asList(testPath);
        when(pathRepository.findAllActive()).thenReturn(paths);

        // When
        Mono<PathsResponse> result = pathService.getAllPaths();

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertNotNull(response.getPaths());
                    assertEquals(1, response.getPaths().size());
                    assertEquals("Test Path", response.getPaths().get(0).getName());
                })
                .verifyComplete();

        verify(pathRepository).findAllActive();
    }

    @Test
    @DisplayName("Should successfully get path by ID")
    void testGetPathById() {
        // Given
        when(pathRepository.findByIdAndActive(1L)).thenReturn(Optional.of(testPath));

        // When
        Mono<PathResponse> result = pathService.getPathById(1L);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(1L, response.getId());
                    assertEquals("Test Path", response.getName());
                    assertEquals("Feature", response.getType());
                })
                .verifyComplete();

        verify(pathRepository).findByIdAndActive(1L);
    }

    @Test
    @DisplayName("Should throw exception when path not found by ID")
    void testGetPathByIdNotFound() {
        // Given
        when(pathRepository.findByIdAndActive(1L)).thenReturn(Optional.empty());

        // When
        Mono<PathResponse> result = pathService.getPathById(1L);

        // Then
        StepVerifier.create(result)
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(pathRepository).findByIdAndActive(1L);
    }

    @Test
    @DisplayName("Should successfully create new path")
    void testCreatePath() {
        // Given
        when(pathRepository.findByNameAndActive("Test Path")).thenReturn(Optional.empty());
        when(pathRepository.save(any(Path.class))).thenReturn(testPath);

        // When
        Mono<PathOperationResult> result = pathService.createPath(testPathRequest, "admin");

        // Then
        StepVerifier.create(result)
                .assertNext(operationResult -> {
                    assertNotNull(operationResult);
                    assertTrue(operationResult.isCreated());
                    assertNotNull(operationResult.getPath());
                    assertEquals("Test Path", operationResult.getPath().getName());
                    assertEquals("Feature", operationResult.getPath().getType());
                })
                .verifyComplete();

        verify(pathRepository).findByNameAndActive("Test Path");
        verify(pathRepository).save(any(Path.class));
    }

    @Test
    @DisplayName("Should update existing path when creating path with existing name")
    void testCreatePathWithExistingName() {
        // Given
        when(pathRepository.findByNameAndActive("Test Path")).thenReturn(Optional.of(testPath));
        when(pathRepository.save(any(Path.class))).thenReturn(testPath);

        // When
        Mono<PathOperationResult> result = pathService.createPath(testPathRequest, "admin");

        // Then
        StepVerifier.create(result)
                .assertNext(operationResult -> {
                    assertNotNull(operationResult);
                    assertFalse(operationResult.isCreated()); // Should be an update
                    assertNotNull(operationResult.getPath());
                    assertEquals("Test Path", operationResult.getPath().getName());
                })
                .verifyComplete();

        verify(pathRepository).findByNameAndActive("Test Path");
        verify(pathRepository).save(any(Path.class));
    }

    @Test
    @DisplayName("Should successfully update path")
    void testUpdatePath() {
        // Given
        when(pathRepository.findByIdAndActive(1L)).thenReturn(Optional.of(testPath));
        when(pathRepository.existsByName("Updated Path")).thenReturn(false);
        when(pathRepository.save(any(Path.class))).thenReturn(testPath);

        testPathRequest.setName("Updated Path");

        // When
        Mono<PathResponse> result = pathService.updatePath(1L, testPathRequest, "admin");

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals("Updated Path", response.getName());
                })
                .verifyComplete();

        verify(pathRepository).findByIdAndActive(1L);
        verify(pathRepository).existsByName("Updated Path");
        verify(pathRepository).save(any(Path.class));
    }

    @Test
    @DisplayName("Should successfully delete path")
    void testDeletePath() {
        // Given
        when(pathRepository.findByIdAndActive(1L)).thenReturn(Optional.of(testPath));
        when(pathRepository.save(any(Path.class))).thenReturn(testPath);

        // When
        Mono<Void> result = pathService.deletePath(1L, "admin");

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(pathRepository).findByIdAndActive(1L);
        verify(pathRepository).save(any(Path.class));
    }

    @Test
    @DisplayName("Should successfully search paths by name")
    void testSearchPathsByName() {
        // Given
        List<Path> paths = Arrays.asList(testPath);
        when(pathRepository.findByNameContainingIgnoreCase("Test")).thenReturn(paths);

        // When
        Mono<PathsResponse> result = pathService.searchPathsByName("Test");

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertNotNull(response.getPaths());
                    assertEquals(1, response.getPaths().size());
                    assertEquals("Test Path", response.getPaths().get(0).getName());
                })
                .verifyComplete();

        verify(pathRepository).findByNameContainingIgnoreCase("Test");
    }
}
