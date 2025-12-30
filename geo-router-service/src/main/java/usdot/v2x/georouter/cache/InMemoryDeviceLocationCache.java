package usdot.v2x.georouter.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import usdot.v2x.georouter.geospatial.GeohashRouter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of DeviceLocationCache.
 * Suitable for single-instance deployments or testing.
 * For production, use PostgresDeviceLocationCache.
 */
@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "geo-routing.cache.type",
    havingValue = "in-memory",
    matchIfMissing = false
)
public class InMemoryDeviceLocationCache implements DeviceLocationCache {

    private final GeohashRouter geohashRouter;
    private final Map<String, DeviceLocation> locations = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> geohashIndex = new ConcurrentHashMap<>(); // geohash -> device IDs

    public InMemoryDeviceLocationCache(GeohashRouter geohashRouter) {
        this.geohashRouter = geohashRouter;
    }

    @Override
    public void updateLocation(String deviceId, double latitude, double longitude, String geohash) {
        // Remove old geohash index entry
        DeviceLocation oldLocation = locations.get(deviceId);
        if (oldLocation != null && oldLocation.getGeohash() != null) {
            Set<String> devices = geohashIndex.get(oldLocation.getGeohash());
            if (devices != null) {
                devices.remove(deviceId);
                if (devices.isEmpty()) {
                    geohashIndex.remove(oldLocation.getGeohash());
                }
            }
        }

        // Create new location entry
        DeviceLocation location = DeviceLocation.builder()
                .deviceId(deviceId)
                .latitude(latitude)
                .longitude(longitude)
                .geohash(geohash)
                .timestamp(System.currentTimeMillis())
                .build();

        locations.put(deviceId, location);

        // Update geohash index
        geohashIndex.computeIfAbsent(geohash, k -> ConcurrentHashMap.newKeySet())
                .add(deviceId);

        log.debug("Updated location for device {}: lat={}, lon={}, geohash={}",
                deviceId, latitude, longitude, geohash);
    }

    @Override
    public DeviceLocation getLocation(String deviceId) {
        return locations.get(deviceId);
    }

    @Override
    public Set<String> findDevicesInRadius(double centerLat, double centerLon, double radiusMeters) {
        Set<String> devicesInRadius = new HashSet<>();
        
        // Simple distance-based search (for small datasets)
        // For large datasets, consider spatial indexing (R-tree, etc.)
        for (DeviceLocation location : locations.values()) {
            double distance = calculateDistance(
                    centerLat, centerLon,
                    location.getLatitude(), location.getLongitude()
            );
            if (distance <= radiusMeters) {
                devicesInRadius.add(location.getDeviceId());
            }
        }
        
        return devicesInRadius;
    }

    @Override
    public Set<String> findDevicesInGeohashes(Set<String> geohashes) {
        Set<String> devices = new HashSet<>();
        
        for (String geohash : geohashes) {
            Set<String> devicesInGeohash = geohashIndex.get(geohash);
            if (devicesInGeohash != null) {
                devices.addAll(devicesInGeohash);
            }
        }
        
        return devices;
    }

    @Override
    public void removeDevice(String deviceId) {
        DeviceLocation location = locations.remove(deviceId);
        if (location != null && location.getGeohash() != null) {
            Set<String> devices = geohashIndex.get(location.getGeohash());
            if (devices != null) {
                devices.remove(deviceId);
                if (devices.isEmpty()) {
                    geohashIndex.remove(location.getGeohash());
                }
            }
        }
        log.debug("Removed device from cache: {}", deviceId);
    }

    @Override
    public int cleanupStaleEntries(int ttlSeconds) {
        int removed = 0;
        List<String> staleDevices = new ArrayList<>();
        
        for (Map.Entry<String, DeviceLocation> entry : locations.entrySet()) {
            if (entry.getValue().isStale(ttlSeconds)) {
                staleDevices.add(entry.getKey());
            }
        }
        
        for (String deviceId : staleDevices) {
            removeDevice(deviceId);
            removed++;
        }
        
        if (removed > 0) {
            log.info("Cleaned up {} stale device location entries", removed);
        }
        
        return removed;
    }

    @Override
    public int getDeviceCount() {
        return locations.size();
    }

    /**
     * Calculates distance between two points using Haversine formula.
     * 
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return Distance in meters
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth radius in meters
        
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
}


