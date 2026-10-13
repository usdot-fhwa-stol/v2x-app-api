package usdot.v2x.app.api.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import usdot.v2x.app.api.config.GeofenceProperties;
import usdot.v2x.app.api.exceptions.GeofenceTooLargeException;
import usdot.v2x.app.api.exceptions.NoAvailableGeohashException;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;

import java.util.function.Supplier;

/**
 * Chooses between a caller-supplied override geofence and a geofence generated
 * from the TIM/MAP message. Overrides that cover more geohashes than
 * {@code geofence.limits.override-geohash-threshold} are discarded so the
 * partner API can deploy over the tighter generated region. Overrides that
 * cannot be converted to geohash coverage (no usable geometry, or evaluation
 * failure) are discarded the same way.
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
     * The geofence chosen for a deployment and whether it is the caller's override.
     *
     * @param geofence    the geofence to deploy
     * @param usedOverride {@code true} when {@code geofence} is the caller's override
     */
    public record Resolution(GeofenceFeatureCollection geofence, boolean usedOverride) {
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
        return resolveWithSource(override, generatedFromMessage).geofence();
    }

    /**
     * Same as {@link #resolve}, but also reports whether the returned geofence is
     * the caller's override. The override is evaluated at most once per call.
     *
     * @param override             caller-supplied override, or {@code null}
     * @param generatedFromMessage supplier used when there is no override or
     *                             the override cannot be used
     * @return the chosen geofence and whether it came from the override
     */
    public Resolution resolveWithSource(
            GeofenceFeatureCollection override,
            Supplier<GeofenceFeatureCollection> generatedFromMessage) {
        if (override == null) {
            return new Resolution(generatedFromMessage.get(), false);
        }
        if (exceedsThreshold(override)) {
            log.info("Override geofence exceeds geohash threshold; generating geofence from message");
            return new Resolution(generatedFromMessage.get(), false);
        }
        return new Resolution(override, true);
    }

    /**
     * Returns {@code true} when {@code override} should be replaced by a
     * message-derived geofence: it exceeds the geohash threshold, exceeds the
     * configured geohash limits, or has no usable geohash coverage.
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
            int count = geohashUtils.countRepresentativeGeohashes(
                    override, geofenceProperties.getGeohash().getPrecision());
            if (count > threshold) {
                log.info("Override geofence covers {} geohashes, exceeding threshold of {}",
                        count, threshold);
                return true;
            }
            return false;
        } catch (GeofenceTooLargeException e) {
            log.info("Override geofence exceeds geohash resource limits; generating geofence from message: {}",
                    e.getMessage());
            return true;
        } catch (NoAvailableGeohashException e) {
            log.warn("Override geofence has no usable geohash coverage; generating geofence from message: {}",
                    e.getMessage());
            return true;
        } catch (RuntimeException e) {
            log.warn("Override geofence could not be evaluated; generating geofence from message: {}",
                    e.getMessage());
            return true;
        }
    }
}
