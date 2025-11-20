package usdot.v2x.app.api.path;

import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.services.PathService;
import usdot.v2x.app.api.models.dto.PathRequest;
import usdot.v2x.app.api.models.dto.PathResponse;
import usdot.v2x.app.api.models.dto.PathsResponse;
import usdot.v2x.app.api.utils.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/prd/v2/paths")
@Tag(name = "Path Management", description = "Path management endpoints for GeoJSON-like path data")
public class PathRestController {

    private final PathService pathService;
    private final SecurityContextUtils securityContextUtils;

    public PathRestController(PathService pathService, SecurityContextUtils securityContextUtils) {
        this.pathService = pathService;
        this.securityContextUtils = securityContextUtils;
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping(produces = "application/json")
    @Operation(summary = "Get all paths", description = "Retrieves all active paths in GeoJSON-like format. " +
            "This endpoint returns a collection of paths with their coordinates, timestamps, and metadata.", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Paths retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PathsResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Mono<ResponseEntity<PathsResponse>> getAllPaths() {
        try {
            return pathService.getAllPaths()
                    .map(paths -> ResponseEntity.ok(paths))
                    .onErrorResume(throwable -> {
                        log.error("Error retrieving paths", throwable);
                        return Mono.error(new RuntimeException("Failed to retrieve paths: " + throwable.getMessage()));
                    });
        } catch (Exception e) {
            log.error("Unexpected error in getAllPaths", e);
            return Mono.error(new RuntimeException("An unexpected error occurred: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping(value = "/{id}", produces = "application/json")
    @Operation(summary = "Get path by ID", description = "Retrieves a specific path by its ID in GeoJSON-like format.", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Path retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PathResponse.class))),
            @ApiResponse(responseCode = "404", description = "Path not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Mono<ResponseEntity<PathResponse>> getPathById(@PathVariable("id") Long id) {
        try {
            return pathService.getPathById(id)
                    .map(path -> ResponseEntity.ok(path))
                    .onErrorResume(throwable -> {
                        log.error("Error retrieving path with id: {}", id, throwable);
                        return Mono.error(new RuntimeException("Failed to retrieve path: " + throwable.getMessage()));
                    });
        } catch (Exception e) {
            log.error("Unexpected error in getPathById for id: {}", id, e);
            return Mono.error(new RuntimeException("An unexpected error occurred: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping(produces = "application/json", consumes = "application/json")
    @Operation(summary = "Create or update path", description = "Creates a new path with GeoJSON-like structure, or updates an existing path if one with the same name already exists. "
            +
            "Only administrators can create/update paths.", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Path created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PathResponse.class))),
            @ApiResponse(responseCode = "200", description = "Path updated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PathResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid path data", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Mono<ResponseEntity<PathResponse>> createPath(@Valid @RequestBody PathRequest pathRequest) {
        try {
            String createdBy = securityContextUtils.determineRequestedBy();

            return pathService.createPath(pathRequest, createdBy)
                    .map(result -> {
                        int statusCode = result.isCreated() ? 201 : 200;
                        return ResponseEntity.status(statusCode).body(result.getPath());
                    })
                    .onErrorResume(throwable -> {
                        log.error("Error creating/updating path", throwable);
                        return Mono
                                .error(new RuntimeException("Failed to create/update path: " + throwable.getMessage()));
                    });
        } catch (Exception e) {
            log.error("Unexpected error in createPath", e);
            return Mono.error(new RuntimeException("An unexpected error occurred: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping(value = "/{id}", produces = "application/json")
    @Operation(summary = "Delete path", description = "Soft deletes a path by setting it as inactive. " +
            "Only administrators can delete paths.", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Path deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Path not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Mono<ResponseEntity<Void>> deletePath(@PathVariable("id") Long id) {
        try {
            String deletedBy = securityContextUtils.determineRequestedBy();

            return pathService.deletePath(id, deletedBy)
                    .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                    .onErrorResume(throwable -> {
                        log.error("Error deleting path with id: {}", id, throwable);
                        return Mono.error(new RuntimeException("Failed to delete path: " + throwable.getMessage()));
                    });
        } catch (Exception e) {
            log.error("Unexpected error in deletePath for id: {}", id, e);
            return Mono.error(new RuntimeException("An unexpected error occurred: " + e.getMessage()));
        }
    }
}
