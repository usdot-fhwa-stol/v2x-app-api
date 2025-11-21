package usdot.v2x.app.api.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;
import usdot.v2x.app.api.models.geofence.GeofenceDeployment;
import usdot.v2x.app.api.models.geofence.GeofenceDeploymentRequest;
import usdot.v2x.app.api.models.geofence.GeofenceDeploymentResponse;
import usdot.v2x.app.api.models.geofence.GeofenceGeohash;
import usdot.v2x.app.api.repositories.GeofenceDeploymentRepository;
import usdot.v2x.app.api.repositories.GeofenceGeohashRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service implementation for geofence deployment operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeofenceDeploymentServiceImpl implements GeofenceDeploymentService {

    private final GeofenceDeploymentRepository geofenceDeploymentRepository;
    private final GeofenceGeohashRepository geofenceGeohashRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public GeofenceDeploymentResponse createGeofenceDeployment(GeofenceDeploymentRequest request) {
        log.info("Creating or updating Geofence deployment for Geofence ID: {}", request.getGeofenceId());

        // Check if Geofence ID already exists
        boolean isUpdate = geofenceDeploymentRepository.existsByGeofenceId(request.getGeofenceId());
        if (isUpdate) {
            log.info("Geofence deployment with ID {} already exists, updating existing record",
                    request.getGeofenceId());
        } else {
            log.info("Creating new Geofence deployment with ID {}", request.getGeofenceId());
        }

        GeofenceDeployment savedDeployment;

        if (isUpdate) {
            // Update existing Geofence deployment
            savedDeployment = updateExistingGeofenceDeployment(request);
        } else {
            // Create new Geofence deployment
            savedDeployment = createNewGeofenceDeployment(request);
        }

        log.info("Successfully {} Geofence deployment for Geofence ID: {}", isUpdate ? "updated" : "created",
                request.getGeofenceId());
        return mapToResponse(savedDeployment);
    }

    @Override
    public GeofenceDeploymentResponse getGeofenceDeployment(String geofenceId) {
        log.debug("Retrieving Geofence deployment for Geofence ID: {}", geofenceId);

        return geofenceDeploymentRepository.findByGeofenceId(geofenceId)
                .map(this::mapToResponse)
                .orElse(null);
    }

    @Override
    public List<GeofenceDeploymentResponse> getGeofenceDeploymentsByGeohash(String geohash) {
        log.debug("Retrieving Geofence deployments for geohash: {}", geohash);

        return geofenceDeploymentRepository.findByGeohash(geohash)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getGeofenceGeohashes(String geofenceId) {
        log.debug("Retrieving geohashes for Geofence ID: {}", geofenceId);

        return geofenceGeohashRepository.findByGeofenceId(geofenceId)
                .stream()
                .map(GeofenceGeohash::getGeohash)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean deactivateGeofenceDeployment(String geofenceId) {
        log.info("Deactivating Geofence deployment for Geofence ID: {}", geofenceId);

        // Check if the geofence exists and is active
        GeofenceDeployment geofenceDeployment = geofenceDeploymentRepository.findByGeofenceId(geofenceId)
                .orElse(null);

        if (geofenceDeployment == null) {
            log.warn("Geofence deployment not found for Geofence ID: {}", geofenceId);
            return false;
        }

        if (!geofenceDeployment.getIsActive()) {
            log.warn("Geofence deployment is already inactive for Geofence ID: {}", geofenceId);
            return true; // Already inactive, return true
        }

        // Use native SQL query to avoid JSONB type casting issues
        String sql = """
                UPDATE geofence_deployments
                SET is_active = false, updated_at = CURRENT_TIMESTAMP
                WHERE geofence_id = ? AND is_active = true
                """;

        int updatedRows = jdbcTemplate.update(sql, geofenceId);

        if (updatedRows > 0) {
            log.info("Successfully deactivated Geofence deployment for Geofence ID: {}", geofenceId);
            return true;
        } else {
            log.warn("No rows were updated for Geofence ID: {}", geofenceId);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean deleteGeofenceDeployment(String geofenceId) {
        log.info("Deleting Geofence deployment for Geofence ID: {}", geofenceId);

        GeofenceDeployment geofenceDeployment = geofenceDeploymentRepository.findByGeofenceId(geofenceId)
                .orElse(null);

        if (geofenceDeployment == null) {
            log.warn("Geofence deployment not found for Geofence ID: {}", geofenceId);
            return false;
        }

        // Delete geohashes first (cascade should handle this, but being explicit)
        geofenceGeohashRepository.deleteByGeofenceDeploymentId(geofenceDeployment.getId());

        // Delete the Geofence deployment
        geofenceDeploymentRepository.delete(geofenceDeployment);

        log.info("Successfully deleted Geofence deployment for Geofence ID: {}", geofenceId);
        return true;
    }

    @Override
    public List<GeofenceDeploymentResponse> getActiveGeofenceDeploymentsByUser(String deployedBy) {
        log.debug("Retrieving active Geofence deployments for user: {}", deployedBy);

        return geofenceDeploymentRepository.findByDeployedByAndIsActive(deployedBy, true)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public int deactivateExpiredGeofenceDeployments() {
        log.info("Deactivating expired Geofence deployments");

        Instant currentTime = Instant.now();
        int deactivatedCount = geofenceDeploymentRepository.deactivateExpiredGeofenceDeployments(currentTime);

        if (deactivatedCount > 0) {
            log.info("Successfully deactivated {} expired Geofence deployments", deactivatedCount);
        } else {
            log.debug("No expired Geofence deployments found to deactivate");
        }

        return deactivatedCount;
    }

    @Override
    public List<GeofenceDeploymentResponse> getExpiredGeofenceDeployments() {
        log.debug("Retrieving expired Geofence deployments");

        return geofenceDeploymentRepository.findByIsActive(false)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<GeofenceDeploymentResponse> getActiveGeofenceDeployments() {
        log.debug("Retrieving all active Geofence deployments");

        return geofenceDeploymentRepository.findByIsActive(true)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private GeofenceDeploymentResponse mapToResponse(GeofenceDeployment geofenceDeployment) {
        // Get geohashes for this deployment
        List<String> geohashes = geofenceGeohashRepository.findByGeofenceId(geofenceDeployment.getGeofenceId())
                .stream()
                .map(GeofenceGeohash::getGeohash)
                .collect(Collectors.toList());

        // Parse geojson string into GeofenceFeatureCollection
        GeofenceFeatureCollection geojsonFeatureCollection = null;
        if (geofenceDeployment.getGeojson() != null && !geofenceDeployment.getGeojson().trim().isEmpty()) {
            try {
                geojsonFeatureCollection = objectMapper.readValue(geofenceDeployment.getGeojson(),
                        GeofenceFeatureCollection.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse geojson for Geofence deployment {}: {}", geofenceDeployment.getGeofenceId(),
                        e.getMessage());
            }
        }

        return GeofenceDeploymentResponse.builder()
                .id(geofenceDeployment.getId())
                .geofenceId(geofenceDeployment.getGeofenceId())
                .geojson(geojsonFeatureCollection)
                .hexPayload(geofenceDeployment.getHexPayload())
                .deployedBy(geofenceDeployment.getDeployedBy())
                .isActive(geofenceDeployment.getIsActive())
                .createdAt(geofenceDeployment.getCreatedAt())
                .updatedAt(geofenceDeployment.getUpdatedAt())
                .expiresAt(geofenceDeployment.getExpiresAt())
                .geohashes(geohashes)
                .build();
    }

    /**
     * Create a new Geofence deployment
     */
    private GeofenceDeployment createNewGeofenceDeployment(GeofenceDeploymentRequest request) {
        // Create Geofence deployment entity
        GeofenceDeployment geofenceDeployment = new GeofenceDeployment();
        geofenceDeployment.setGeofenceId(request.getGeofenceId());

        // Convert GeofenceFeatureCollection to JSON string for database storage
        try {
            String geojsonString = objectMapper.writeValueAsString(request.getGeojson());
            geofenceDeployment.setGeojson(geojsonString);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert GeofenceFeatureCollection to JSON string: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert geofence to JSON", e);
        }

        geofenceDeployment.setHexPayload(request.getHexPayload());
        geofenceDeployment.setMsgType(request.getMsgType());
        geofenceDeployment.setDeployedBy(request.getDeployedBy());
        geofenceDeployment.setIsActive(true);

        // Parse expiration time if provided
        if (request.getExpiresAt() != null && !request.getExpiresAt().trim().isEmpty()) {
            try {
                geofenceDeployment.setExpiresAt(Instant.parse(request.getExpiresAt()));
            } catch (DateTimeParseException e) {
                log.warn("Invalid expiration time format: {}, ignoring", request.getExpiresAt());
            }
        }

        // Save the Geofence deployment using native query to handle JSONB properly
        GeofenceDeployment savedDeployment = saveGeofenceDeploymentWithJsonb(geofenceDeployment);

        // Create and save geohashes
        if (request.getGeohashes() != null && !request.getGeohashes().isEmpty()) {
            Instant now = Instant.now();
            List<GeofenceGeohash> geohashes = request.getGeohashes().stream()
                    .map(geohash -> {
                        GeofenceGeohash geofenceGeohash = new GeofenceGeohash();
                        geofenceGeohash.setGeofenceDeployment(savedDeployment);
                        geofenceGeohash.setGeohash(geohash);
                        geofenceGeohash.setCreatedAt(now);
                        return geofenceGeohash;
                    })
                    .collect(Collectors.toList());

            geofenceGeohashRepository.saveAll(geohashes);
        }

        return savedDeployment;
    }

    /**
     * Update an existing Geofence deployment
     */
    private GeofenceDeployment updateExistingGeofenceDeployment(GeofenceDeploymentRequest request) {
        // Find existing deployment
        GeofenceDeployment existingDeployment = geofenceDeploymentRepository.findByGeofenceId(request.getGeofenceId())
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Geofence deployment not found for update: " + request.getGeofenceId()));

        // Parse expiration time if provided
        Instant expiresAt = null;
        if (request.getExpiresAt() != null && !request.getExpiresAt().trim().isEmpty()) {
            try {
                expiresAt = Instant.parse(request.getExpiresAt());
            } catch (DateTimeParseException e) {
                log.warn("Invalid expiration time format: {}, ignoring", request.getExpiresAt());
            }
        }

        // Convert GeofenceFeatureCollection to JSON string for database storage
        String geojsonString;
        try {
            geojsonString = objectMapper.writeValueAsString(request.getGeojson());
        } catch (JsonProcessingException e) {
            log.error("Failed to convert GeofenceFeatureCollection to JSON string: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert geofence to JSON", e);
        }

        // Update the Geofence deployment using native query to handle JSONB properly
        GeofenceDeployment savedDeployment = updateGeofenceDeploymentWithJsonb(
                existingDeployment.getId(),
                geojsonString,
                request.getHexPayload(),
                request.getMsgType(),
                request.getDeployedBy(),
                true, // isActive
                expiresAt);

        // Update geohashes - delete existing and create new ones
        if (request.getGeohashes() != null && !request.getGeohashes().isEmpty()) {
            // Delete existing geohashes using native query to avoid Hibernate issues
            deleteGeohashesByGeofenceDeploymentId(savedDeployment.getId());

            // Create new geohashes
            Instant now = Instant.now();
            List<GeofenceGeohash> geohashes = request.getGeohashes().stream()
                    .map(geohash -> {
                        GeofenceGeohash geofenceGeohash = new GeofenceGeohash();
                        geofenceGeohash.setGeofenceDeployment(savedDeployment);
                        geofenceGeohash.setGeohash(geohash);
                        geofenceGeohash.setCreatedAt(now);
                        return geofenceGeohash;
                    })
                    .collect(Collectors.toList());

            geofenceGeohashRepository.saveAll(geohashes);
        }

        return savedDeployment;
    }

    /**
     * Save Geofence deployment using native query to handle JSONB properly
     */
    private GeofenceDeployment saveGeofenceDeploymentWithJsonb(GeofenceDeployment geofenceDeployment) {
        String sql = """
                INSERT INTO geofence_deployments (geofence_id, geojson, msg_type, hex_payload, deployed_by, is_active, expires_at)
                VALUES (?, ?::jsonb, ?, ?, ?, ?, ?::timestamp)
                RETURNING id, created_at, updated_at
                """;

        return jdbcTemplate.queryForObject(sql, (rs, _) -> {
            GeofenceDeployment saved = new GeofenceDeployment();
            saved.setId(rs.getLong("id"));
            saved.setGeofenceId(geofenceDeployment.getGeofenceId());
            saved.setGeojson(geofenceDeployment.getGeojson());
            saved.setMsgType(geofenceDeployment.getMsgType());
            saved.setHexPayload(geofenceDeployment.getHexPayload());
            saved.setDeployedBy(geofenceDeployment.getDeployedBy());
            saved.setIsActive(geofenceDeployment.getIsActive());
            saved.setExpiresAt(geofenceDeployment.getExpiresAt());
            saved.setCreatedAt(rs.getTimestamp("created_at").toInstant());
            saved.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
            return saved;
        },
                geofenceDeployment.getGeofenceId(),
                geofenceDeployment.getGeojson(),
                geofenceDeployment.getMsgType(),
                geofenceDeployment.getHexPayload(),
                geofenceDeployment.getDeployedBy(),
                geofenceDeployment.getIsActive(),
                geofenceDeployment.getExpiresAt() != null ? java.sql.Timestamp.from(geofenceDeployment.getExpiresAt())
                        : null);
    }

    /**
     * Update Geofence deployment using native query to handle JSONB properly
     */
    private GeofenceDeployment updateGeofenceDeploymentWithJsonb(Long id, String geojson, String hexPayload,
            String msgType, String deployedBy, Boolean isActive, Instant expiresAt) {
        String sql = """
                UPDATE geofence_deployments
                SET geojson = ?::jsonb, hex_payload = ?, msg_type = ?, deployed_by = ?, is_active = ?, expires_at = ?::timestamp, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                RETURNING id, geofence_id, created_at, updated_at
                """;

        return jdbcTemplate.queryForObject(sql, (rs, _) -> {
            GeofenceDeployment updated = new GeofenceDeployment();
            updated.setId(rs.getLong("id"));
            updated.setGeofenceId(rs.getString("geofence_id"));
            updated.setGeojson(geojson);
            updated.setHexPayload(hexPayload);
            updated.setMsgType(msgType);
            updated.setDeployedBy(deployedBy);
            updated.setIsActive(isActive);
            updated.setExpiresAt(expiresAt);
            updated.setCreatedAt(rs.getTimestamp("created_at").toInstant());
            updated.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
            return updated;
        },
                geojson,
                hexPayload,
                msgType,
                deployedBy,
                isActive,
                expiresAt != null ? java.sql.Timestamp.from(expiresAt) : null,
                id);
    }

    /**
     * Delete geohashes by Geofence deployment ID using native query
     */
    private void deleteGeohashesByGeofenceDeploymentId(Long geofenceDeploymentId) {
        String sql = "DELETE FROM geofence_geohashes WHERE geofence_deployment_id = ?";
        jdbcTemplate.update(sql, geofenceDeploymentId);
    }
}
