package usdot.v2x.app.api.services;

import usdot.v2x.app.api.models.Path;
import usdot.v2x.app.api.models.dto.PathRequest;
import usdot.v2x.app.api.models.dto.PathResponse;
import usdot.v2x.app.api.models.dto.PathsResponse;
import usdot.v2x.app.api.models.dto.PathOperationResult;
import usdot.v2x.app.api.repositories.PathRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PathServiceImpl implements PathService {

    @Autowired
    private PathRepository pathRepository;

    @Override
    public Mono<PathsResponse> getAllPaths() {
        return Mono.fromCallable(() -> {
            List<Path> paths = pathRepository.findAllActive();
            List<PathResponse> pathResponses = paths.stream()
                    .map(PathResponse::fromEntity)
                    .collect(Collectors.toList());
            return new PathsResponse(pathResponses);
        });
    }

    @Override
    public Mono<PathResponse> getPathById(Long id) {
        return Mono.fromCallable(() -> {
            Path path = pathRepository.findByIdAndActive(id)
                    .orElseThrow(() -> new IllegalArgumentException("Path not found with id: " + id));
            return PathResponse.fromEntity(path);
        });
    }

    @Override
    @Transactional
    public Mono<PathOperationResult> createPath(PathRequest pathRequest, String createdBy) {
        return Mono.fromCallable(() -> {
            // Check if path with same name already exists
            Optional<Path> existingPath = pathRepository.findByNameAndActive(pathRequest.getName());

            Path path;
            boolean isUpdate = existingPath.isPresent();

            if (isUpdate) {
                // Update existing path
                path = existingPath.get();
                path.setType(pathRequest.getType() != null ? pathRequest.getType() : "Feature");
                path.setGeometryType(pathRequest.getGeometry().getType());
                path.setUpdatedBy(createdBy);
                path.setUpdatedAt(Instant.now());
                log.info("Updating existing path with id: {}, name: {}, updatedBy: {}", path.getId(), path.getName(),
                        createdBy);
            } else {
                // Create new path
                path = new Path();
                path.setName(pathRequest.getName());
                path.setType(pathRequest.getType() != null ? pathRequest.getType() : "Feature");
                path.setGeometryType(pathRequest.getGeometry().getType());
                path.setIsActive(true);
                path.setCreatedBy(createdBy);
                path.setUpdatedBy(createdBy);
                path.setCreatedAt(Instant.now());
                path.setUpdatedAt(Instant.now());
                log.info("Creating new path with name: {}, createdBy: {}", pathRequest.getName(), createdBy);
            }

            // Convert coordinates to string format for storage
            List<String> coordinateStrings = pathRequest.getGeometry().getCoordinates().stream()
                    .map(coord -> "[" + coord.get(0) + "," + coord.get(1) + "]")
                    .collect(Collectors.toList());
            path.setCoordinates(coordinateStrings);

            path.setTimestamps(pathRequest.getTimestamps());

            Path savedPath = pathRepository.save(path);
            log.info("{} path with id: {}, name: {}, {}: {}",
                    isUpdate ? "Updated" : "Created",
                    savedPath.getId(),
                    savedPath.getName(),
                    isUpdate ? "updatedBy" : "createdBy",
                    createdBy);

            PathResponse pathResponse = PathResponse.fromEntity(savedPath);
            return new PathOperationResult(pathResponse, !isUpdate);
        });
    }

    @Override
    @Transactional
    public Mono<PathResponse> updatePath(Long id, PathRequest pathRequest, String updatedBy) {
        return Mono.fromCallable(() -> {
            Path existingPath = pathRepository.findByIdAndActive(id)
                    .orElseThrow(() -> new IllegalArgumentException("Path not found with id: " + id));

            // Check if another path with the same name exists (excluding current path)
            if (!existingPath.getName().equals(pathRequest.getName()) &&
                    pathRepository.existsByName(pathRequest.getName())) {
                throw new IllegalArgumentException("Path with name '" + pathRequest.getName() + "' already exists");
            }

            existingPath.setName(pathRequest.getName());
            existingPath.setType(pathRequest.getType() != null ? pathRequest.getType() : "Feature");
            existingPath.setGeometryType(pathRequest.getGeometry().getType());
            existingPath.setUpdatedBy(updatedBy);
            existingPath.setUpdatedAt(Instant.now());

            // Convert coordinates to string format for storage
            List<String> coordinateStrings = pathRequest.getGeometry().getCoordinates().stream()
                    .map(coord -> "[" + coord.get(0) + "," + coord.get(1) + "]")
                    .collect(Collectors.toList());
            existingPath.setCoordinates(coordinateStrings);

            existingPath.setTimestamps(pathRequest.getTimestamps());

            Path savedPath = pathRepository.save(existingPath);
            log.info("Updated path with id: {}, name: {}, updatedBy: {}", savedPath.getId(), savedPath.getName(),
                    updatedBy);
            return PathResponse.fromEntity(savedPath);
        });
    }

    @Override
    @Transactional
    public Mono<Void> deletePath(Long id, String deletedBy) {
        return Mono.fromRunnable(() -> {
            Path path = pathRepository.findByIdAndActive(id)
                    .orElseThrow(() -> new IllegalArgumentException("Path not found with id: " + id));

            path.setIsActive(false);
            path.setUpdatedBy(deletedBy);
            path.setUpdatedAt(Instant.now());
            pathRepository.save(path);

            log.info("Soft deleted path with id: {}, name: {}, deletedBy: {}", path.getId(), path.getName(), deletedBy);
        });
    }

    @Override
    public Mono<PathsResponse> searchPathsByName(String name) {
        return Mono.fromCallable(() -> {
            List<Path> paths = pathRepository.findByNameContainingIgnoreCase(name);
            List<PathResponse> pathResponses = paths.stream()
                    .map(PathResponse::fromEntity)
                    .collect(Collectors.toList());
            return new PathsResponse(pathResponses);
        });
    }
}
