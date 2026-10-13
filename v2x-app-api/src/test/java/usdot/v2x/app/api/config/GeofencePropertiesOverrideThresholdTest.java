package usdot.v2x.app.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GeofencePropertiesOverrideThresholdTest {

    @Test
    void bindsOverrideGeohashThresholdFromKebabCase() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "geofence.limits.override-geohash-threshold", "12"));

        GeofenceProperties properties = new Binder(source)
                .bind("geofence", GeofenceProperties.class)
                .get();

        assertEquals(12, properties.getLimits().getOverrideGeohashThreshold());
    }

    @Test
    void bindsRepresentativeFilterThresholdFromKebabCase() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "geofence.limits.representative-filter-threshold", "8"));

        GeofenceProperties properties = new Binder(source)
                .bind("geofence", GeofenceProperties.class)
                .get();

        assertEquals(8, properties.getLimits().getRepresentativeFilterThreshold());
    }

    @Test
    void representativeFilterThresholdDefaultsToTwenty() {
        assertEquals(20, new GeofenceProperties().getLimits().getRepresentativeFilterThreshold());
    }

    @Test
    void overrideGeohashThresholdDefaultsToFifty() {
        assertEquals(50, new GeofenceProperties().getLimits().getOverrideGeohashThreshold());
    }
}
