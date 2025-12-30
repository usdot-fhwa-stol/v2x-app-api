package usdot.v2x.georouter.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a device's current location and metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceLocation {
    private String deviceId;
    private double latitude;
    private double longitude;
    private long timestamp; // Unix timestamp in milliseconds
    private String geohash; // Cached geohash for quick lookups
    
    /**
     * Checks if this location is stale based on TTL.
     * 
     * @param ttlSeconds Time-to-live in seconds
     * @return true if location is stale (older than TTL)
     */
    public boolean isStale(int ttlSeconds) {
        long ageSeconds = (System.currentTimeMillis() - timestamp) / 1000;
        return ageSeconds > ttlSeconds;
    }
    
    /**
     * Gets the age of this location in seconds.
     */
    public long getAgeSeconds() {
        return (System.currentTimeMillis() - timestamp) / 1000;
    }
}

