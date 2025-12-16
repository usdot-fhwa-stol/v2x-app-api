package usdot.v2x.app.api.services;

import usdot.v2x.app.api.models.dto.PathRequest;
import usdot.v2x.app.api.models.dto.PathResponse;
import usdot.v2x.app.api.models.dto.PathsResponse;
import usdot.v2x.app.api.models.dto.PathOperationResult;
import reactor.core.publisher.Mono;

/**
 * Service interface for path management operations.
 */
public interface PathService {

    /**
     * Get all active paths
     */
    Mono<PathsResponse> getAllPaths();

    /**
     * Get path by ID
     */
    Mono<PathResponse> getPathById(Long id);

    /**
     * Create a new path or update existing path with same name
     */
    Mono<PathOperationResult> createPath(PathRequest pathRequest, String createdBy);

    /**
     * Update an existing path
     */
    Mono<PathResponse> updatePath(Long id, PathRequest pathRequest, String updatedBy);

    /**
     * Delete a path (soft delete)
     */
    Mono<Void> deletePath(Long id, String deletedBy);

    /**
     * Search paths by name
     */
    Mono<PathsResponse> searchPathsByName(String name);
}
