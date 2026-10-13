package usdot.v2x.app.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GeometryPropertiesTest {

    @Test
    void bindsPathLengthOffsetFromRelaxedPropertyNames() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "geometry.geofence-offset-meters", "50.0",
                "geometry.geofence-path-length-offset-meters", "250.5",
                "geometry.geofence-path-extension-tip-width-meters", "12.0",
                "geometry.default-lane-width-cm", "1500.0"));

        GeometryProperties properties = new Binder(source)
                .bind("geometry", GeometryProperties.class)
                .get();

        assertEquals(50.0, properties.getGeofenceOffsetMeters());
        assertEquals(250.5, properties.getGeofencePathLengthOffsetMeters());
        assertEquals(12.0, properties.getGeofencePathExtensionTipWidthMeters());
        assertEquals(1500.0, properties.getDefaultLaneWidthCm());
    }

    @Test
    void pathExtensionTipWidthDefaultsToZeroWhenUnbound() {
        GeometryProperties properties = new GeometryProperties();

        assertEquals(0.0, properties.getGeofencePathExtensionTipWidthMeters());
    }

    @Test
    void pathLengthOffsetDefaultsToZeroWhenUnbound() {
        GeometryProperties properties = new GeometryProperties();

        assertEquals(0.0, properties.getGeofencePathLengthOffsetMeters());
    }
}
