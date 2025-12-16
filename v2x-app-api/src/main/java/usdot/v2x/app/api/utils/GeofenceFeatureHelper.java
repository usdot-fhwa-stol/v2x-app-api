package usdot.v2x.app.api.utils;

import org.locationtech.jts.geom.Geometry;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeature;

/**
 * Helper class for working with GeofenceFeature objects.
 * Provides convenient methods for setting JTS geometries with automatic
 * conversion to custom geometry objects.
 */
public class GeofenceFeatureHelper {

    /**
     * Sets a JTS geometry in a GeofenceFeature, automatically converting it to the
     * custom geometry type.
     * 
     * @param feature     The GeofenceFeature to update
     * @param jtsGeometry The JTS Geometry to set (will be converted to custom
     *                    geometry)
     * @throws IllegalArgumentException if the geometry type is not supported
     */
    public static void setGeometry(GeofenceFeature feature, Geometry jtsGeometry) {
        if (jtsGeometry == null) {
            feature.setGeometry(null);
            return;
        }

        usdot.v2x.app.api.models.etx.configuration.geometry.Geometry customGeometry = GeometryConverter
                .fromJtsGeometry(jtsGeometry);
        feature.setGeometry(customGeometry);
    }

    /**
     * Creates a new GeofenceFeature with the specified JTS geometry.
     * 
     * @param type        The feature type
     * @param jtsGeometry The JTS Geometry to set (will be converted to custom
     *                    geometry)
     * @param properties  The feature properties
     * @return A new GeofenceFeature with the converted geometry
     * @throws IllegalArgumentException if the geometry type is not supported
     */
    public static GeofenceFeature createFeature(String type, Geometry jtsGeometry, Object properties) {
        GeofenceFeature feature = new GeofenceFeature();
        feature.setType(type);
        setGeometry(feature, jtsGeometry);
        feature.setProperties(properties);
        return feature;
    }

    /**
     * Gets the JTS geometry from a GeofenceFeature, converting from custom geometry
     * if necessary.
     * 
     * @param feature The GeofenceFeature to get the geometry from
     * @return The JTS Geometry, or null if the feature has no geometry
     * @throws IllegalArgumentException if the geometry type is not supported
     */
    public static Geometry getJtsGeometry(GeofenceFeature feature) {
        usdot.v2x.app.api.models.etx.configuration.geometry.Geometry customGeometry = feature.getGeometry();
        if (customGeometry == null) {
            return null;
        }

        return GeometryConverter.toJtsGeometry(customGeometry);
    }
}