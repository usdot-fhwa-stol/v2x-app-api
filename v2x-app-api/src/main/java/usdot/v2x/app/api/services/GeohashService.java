package usdot.v2x.app.api.services;

import ch.hsr.geohash.GeoHash;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for geohash calculations.
 */
@Slf4j
@Service
public class GeohashService {

    /**
     * Calculate geohash for given coordinates.
     * 
     * @param latitude Latitude in decimal degrees
     * @param longitude Longitude in decimal degrees
     * @param precision Geohash precision (number of characters, typically 7)
     * @return Geohash string
     */
    public String calculateGeohash(double latitude, double longitude, int precision) {
        try {
            GeoHash geoHash = GeoHash.withCharacterPrecision(latitude, longitude, precision);
            return geoHash.toBase32();
        } catch (Exception e) {
            log.error("Error calculating geohash for lat={}, lon={}, precision={}: {}",
                    latitude, longitude, precision, e.getMessage());
            throw new RuntimeException("Failed to calculate geohash", e);
        }
    }

    /**
     * Calculate geohash with default precision of 7.
     */
    public String calculateGeohash(double latitude, double longitude) {
        return calculateGeohash(latitude, longitude, 7);
    }
}



