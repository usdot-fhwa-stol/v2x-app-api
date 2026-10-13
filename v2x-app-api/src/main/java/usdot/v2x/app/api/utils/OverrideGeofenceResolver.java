package usdot.v2x.app.api.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import usdot.v2x.app.api.config.GeofenceProperties;
import usdot.v2x.app.api.exceptions.GeofenceTooLargeException;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Chooses between a caller-supplied override geofence and a geofence generated
 * from the TIM/MAP message. Overrides that cover more geohashes than
 * {@code geofence.limits.override-geohash-threshold} are discarded so the
 * partner API can deploy over the tighter generated region.
 */
@Component
@Slf4j
public class OverrideGeofenceResolver {

    private final GeohashUtils geohashUtils;
    private final GeofenceProperties geofenceProperties;

    public OverrideGeofenceResolver(GeohashUtils geohashUtils, GeofenceProperties geofenceProperties) {
        this.geohashUtils = geohashUtils;
        this.geofenceProperties = geofenceProperties;
    }

    /**
     * @param override             caller-supplied override, or {@code null}
     * @param generatedFromMessage supplier used when there is no override or
     *                             the override exceeds the geohash threshold
     * @return the geofence that should be used for the deployment
     */
    public GeofenceFeatureCollection resolve(
            GeofenceFeatureCollection override,
            Supplier<GeofenceFeatureCollection> generatedFromMessage) {
        if (override == null) {
            return generatedFromMessage.get();
        }
        if (exceedsThreshold(override)) {
            log.info("Override geofence exceeds geohash threshold; generating geofence from message");
            return generatedFromMessage.get();
        }
        return override;
    }

    /**
     * Returns {@code true} when {@code override} should be replaced by a
     * message-derived geofence.
     */
    public boolean exceedsThreshold(GeofenceFeatureCollection override) {
        if (override == null) {
            return false;
        }
        int threshold = geofenceProperties.getLimits().getOverrideGeohashThreshold();
        if (threshold <= 0) {
            log.info("Override geohash threshold is {}; generating geofence from message", threshold);
            return true;
        }
        try {
            List<String> geohashes = geohashUtils.extractGeohashesFromGeofenceFeatureCollection(
                    override, geofenceProperties.getGeohash().getPrecision(), Set.of());
            if (geohashes.size() > threshold) {
                log.info("Override geofence covers {} geohashes, exceeding threshold of {}",
                        geohashes.size(), threshold);
                return true;
            }
            return false;
        } catch (GeofenceTooLargeException e) {
            log.info("Override geofence exceeds geohash resource limits; generating geofence from message: {}",
                    e.getMessage());
            return true;
        }
    }
}
