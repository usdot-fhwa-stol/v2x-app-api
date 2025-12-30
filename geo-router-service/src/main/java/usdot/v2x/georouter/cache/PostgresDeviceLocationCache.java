package usdot.v2x.georouter.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * PostgreSQL-based implementation of DeviceLocationCache.
 * Queries the mqtt_device_locations table in the v2x-app-api database.
 */
@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "geo-routing.cache.type",
    havingValue = "postgres",
    matchIfMissing = true
)
public class PostgresDeviceLocationCache implements DeviceLocationCache {

    private final JdbcTemplate jdbcTemplate;

    public PostgresDeviceLocationCache(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void updateLocation(String deviceId, double latitude, double longitude, String geohash) {
        // This is handled by the REST API, so we just log
        log.debug("Location update should be done via REST API for device: {}", deviceId);
    }

    @Override
    public DeviceLocation getLocation(String deviceId) {
        String sql = "SELECT mqtt_client_id, latitude, longitude, geohash, last_updated " +
                     "FROM mqtt_device_locations " +
                     "WHERE mqtt_client_id = ? AND is_active = true";
        
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                DeviceLocation location = DeviceLocation.builder()
                        .deviceId(rs.getString("mqtt_client_id"))
                        .latitude(rs.getDouble("latitude"))
                        .longitude(rs.getDouble("longitude"))
                        .geohash(rs.getString("geohash"))
                        .timestamp(rs.getTimestamp("last_updated").toInstant().toEpochMilli())
                        .build();
                return location;
            }, deviceId);
        } catch (Exception e) {
            log.debug("Device location not found in database: {}", deviceId);
            return null;
        }
    }

    @Override
    public Set<String> findDevicesInRadius(double centerLat, double centerLon, double radiusMeters) {
        // Use PostGIS ST_DWithin for efficient spatial queries
        String sql = "SELECT mqtt_client_id FROM mqtt_device_locations " +
                     "WHERE is_active = true " +
                     "AND ST_DWithin(" +
                     "  ST_MakePoint(longitude, latitude)::geography, " +
                     "  ST_MakePoint(?, ?)::geography, " +
                     "  ?" +
                     ")";
        
        try {
            Set<String> deviceIds = new HashSet<>(
                    jdbcTemplate.queryForList(sql, String.class, centerLon, centerLat, radiusMeters));
            return deviceIds;
        } catch (Exception e) {
            log.error("Error querying devices in radius", e);
            return new HashSet<>();
        }
    }

    @Override
    public Set<String> findDevicesInGeohashes(Set<String> geohashes) {
        if (geohashes == null || geohashes.isEmpty()) {
            return new HashSet<>();
        }

        // Use IN clause with placeholders
        try {
            String placeholders = String.join(",", java.util.Collections.nCopies(geohashes.size(), "?"));
            String sql = "SELECT mqtt_client_id FROM mqtt_device_locations " +
                         "WHERE geohash IN (" + placeholders + ") AND is_active = true";
            Set<String> deviceIds = new HashSet<>(
                    jdbcTemplate.queryForList(sql, String.class, geohashes.toArray()));
            return deviceIds;
        } catch (Exception e) {
            log.error("Error querying devices in geohashes", e);
            return new HashSet<>();
        }
    }

    @Override
    public void removeDevice(String deviceId) {
        String sql = "UPDATE mqtt_device_locations SET is_active = false, last_updated = ? " +
                     "WHERE mqtt_client_id = ?";
        
        try {
            jdbcTemplate.update(sql, Instant.now(), deviceId);
            log.debug("Deactivated device in database: {}", deviceId);
        } catch (Exception e) {
            log.error("Error removing device from database", e);
        }
    }

    @Override
    public int cleanupStaleEntries(int ttlSeconds) {
        String sql = "UPDATE mqtt_device_locations SET is_active = false " +
                     "WHERE last_updated < NOW() - INTERVAL '? seconds' AND is_active = true";
        
        try {
            int updated = jdbcTemplate.update(sql, ttlSeconds);
            if (updated > 0) {
                log.info("Cleaned up {} stale device location entries", updated);
            }
            return updated;
        } catch (Exception e) {
            log.error("Error cleaning up stale entries", e);
            return 0;
        }
    }

    @Override
    public int getDeviceCount() {
        String sql = "SELECT COUNT(*) FROM mqtt_device_locations WHERE is_active = true";
        
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Error getting device count", e);
            return 0;
        }
    }
}

