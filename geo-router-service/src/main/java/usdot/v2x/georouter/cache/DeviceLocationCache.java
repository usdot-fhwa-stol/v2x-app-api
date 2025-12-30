package usdot.v2x.georouter.cache;

import java.util.Set;

/**
 * Interface for caching device locations.
 * Implementations can use in-memory cache, Redis, or other storage.
 */
public interface DeviceLocationCache {
    
    /**
     * Updates or creates a device location entry.
     * 
     * @param deviceId Device/client identifier
     * @param latitude Latitude in decimal degrees
     * @param longitude Longitude in decimal degrees
     * @param geohash Geohash string for the location
     */
    void updateLocation(String deviceId, double latitude, double longitude, String geohash);
    
    /**
     * Gets the current location of a device.
     * 
     * @param deviceId Device/client identifier
     * @return DeviceLocation or null if not found
     */
    DeviceLocation getLocation(String deviceId);
    
    /**
     * Finds all devices within a specified radius of a point.
     * 
     * @param centerLat Center latitude in decimal degrees
     * @param centerLon Center longitude in decimal degrees
     * @param radiusMeters Radius in meters
     * @return Set of device IDs within the radius
     */
    Set<String> findDevicesInRadius(double centerLat, double centerLon, double radiusMeters);
    
    /**
     * Finds all devices in the given geohashes.
     * 
     * @param geohashes Set of geohash strings
     * @return Set of device IDs in any of the geohashes
     */
    Set<String> findDevicesInGeohashes(Set<String> geohashes);
    
    /**
     * Removes a device from the cache (e.g., on disconnect).
     * 
     * @param deviceId Device/client identifier
     */
    void removeDevice(String deviceId);
    
    /**
     * Cleans up stale entries older than the specified TTL.
     * 
     * @param ttlSeconds Time-to-live in seconds
     * @return Number of entries removed
     */
    int cleanupStaleEntries(int ttlSeconds);
    
    /**
     * Gets the total number of devices in the cache.
     */
    int getDeviceCount();
}




