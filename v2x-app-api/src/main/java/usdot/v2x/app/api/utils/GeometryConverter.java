package usdot.v2x.app.api.utils;

import org.locationtech.jts.geom.*;
import usdot.v2x.app.api.models.etx.configuration.geometry.Geometry;
import usdot.v2x.app.api.models.etx.configuration.geometry.LineString;
import usdot.v2x.app.api.models.etx.configuration.geometry.Polygon;
import usdot.v2x.app.api.models.etx.configuration.geometry.MultiLineString;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for converting between JTS Geometry objects and custom geometry
 * objects.
 * Supports conversion between JTS Geometry and custom GeoJSON-like geometry
 * objects.
 */
public class GeometryConverter {

    private static final GeometryFactory geometryFactory = GeometryUtils.getGeometryFactory();

    /**
     * Converts a JTS Geometry to the appropriate custom geometry object.
     * 
     * @param jtsGeometry The JTS Geometry to convert
     * @return The corresponding custom geometry object
     * @throws IllegalArgumentException if the geometry type is not supported
     */
    public static Geometry fromJtsGeometry(org.locationtech.jts.geom.Geometry jtsGeometry) {
        if (jtsGeometry == null) {
            return null;
        }

        String geometryType = jtsGeometry.getGeometryType();

        switch (geometryType) {
            case "Point":
                return convertPoint((org.locationtech.jts.geom.Point) jtsGeometry);
            case "LineString":
                return convertLineString((org.locationtech.jts.geom.LineString) jtsGeometry);
            case "Polygon":
                return convertPolygon((org.locationtech.jts.geom.Polygon) jtsGeometry);
            case "MultiLineString":
                return convertMultiLineString((org.locationtech.jts.geom.MultiLineString) jtsGeometry);
            case "MultiPolygon":
                return convertMultiPolygon((org.locationtech.jts.geom.MultiPolygon) jtsGeometry);
            default:
                throw new IllegalArgumentException("Unsupported geometry type: " + geometryType);
        }
    }

    /**
     * Converts a custom geometry object to a JTS Geometry.
     * 
     * @param geometry The custom geometry object to convert
     * @return The corresponding JTS Geometry
     * @throws IllegalArgumentException if the geometry type is not supported
     */
    public static org.locationtech.jts.geom.Geometry toJtsGeometry(Geometry geometry) {
        if (geometry == null) {
            return null;
        }

        if (geometry instanceof LineString) {
            return convertToJtsLineString((LineString) geometry);
        } else if (geometry instanceof Polygon) {
            return convertToJtsPolygon((Polygon) geometry);
        } else if (geometry instanceof MultiLineString) {
            return convertToJtsMultiLineString((MultiLineString) geometry);
        } else if (geometry instanceof usdot.v2x.app.api.models.etx.configuration.geometry.MultiPolygon) {
            return convertToJtsMultiPolygon(
                    (usdot.v2x.app.api.models.etx.configuration.geometry.MultiPolygon) geometry);
        } else {
            throw new IllegalArgumentException("Unsupported geometry type: " + geometry.getClass().getSimpleName());
        }
    }

    // Conversion methods from JTS to custom geometry objects

    private static usdot.v2x.app.api.models.etx.configuration.geometry.LineString convertLineString(
            org.locationtech.jts.geom.LineString jtsLineString) {
        usdot.v2x.app.api.models.etx.configuration.geometry.LineString lineString = new usdot.v2x.app.api.models.etx.configuration.geometry.LineString();

        List<List<Double>> coordinates = new ArrayList<>();
        Coordinate[] coords = jtsLineString.getCoordinates();

        for (Coordinate coord : coords) {
            List<Double> point = new ArrayList<>();
            point.add(coord.x); // longitude
            point.add(coord.y); // latitude
            coordinates.add(point);
        }

        lineString.setCoordinates(coordinates);
        return lineString;
    }

    private static usdot.v2x.app.api.models.etx.configuration.geometry.Polygon convertPolygon(
            org.locationtech.jts.geom.Polygon jtsPolygon) {
        usdot.v2x.app.api.models.etx.configuration.geometry.Polygon polygon = new usdot.v2x.app.api.models.etx.configuration.geometry.Polygon();

        List<List<List<Double>>> coordinates = new ArrayList<>();

        // Add exterior ring
        List<List<Double>> exteriorRing = new ArrayList<>();
        Coordinate[] exteriorCoords = jtsPolygon.getExteriorRing().getCoordinates();
        for (Coordinate coord : exteriorCoords) {
            List<Double> point = new ArrayList<>();
            point.add(coord.x); // longitude
            point.add(coord.y); // latitude
            exteriorRing.add(point);
        }
        coordinates.add(exteriorRing);

        // Add interior rings (holes)
        for (int i = 0; i < jtsPolygon.getNumInteriorRing(); i++) {
            List<List<Double>> interiorRing = new ArrayList<>();
            Coordinate[] interiorCoords = jtsPolygon.getInteriorRingN(i).getCoordinates();
            for (Coordinate coord : interiorCoords) {
                List<Double> point = new ArrayList<>();
                point.add(coord.x); // longitude
                point.add(coord.y); // latitude
                interiorRing.add(point);
            }
            coordinates.add(interiorRing);
        }

        polygon.setCoordinates(coordinates);
        return polygon;
    }

    private static usdot.v2x.app.api.models.etx.configuration.geometry.MultiLineString convertMultiLineString(
            org.locationtech.jts.geom.MultiLineString jtsMultiLineString) {
        usdot.v2x.app.api.models.etx.configuration.geometry.MultiLineString multiLineString = new usdot.v2x.app.api.models.etx.configuration.geometry.MultiLineString();

        List<List<List<Double>>> coordinates = new ArrayList<>();

        for (int i = 0; i < jtsMultiLineString.getNumGeometries(); i++) {
            org.locationtech.jts.geom.LineString lineString = (org.locationtech.jts.geom.LineString) jtsMultiLineString
                    .getGeometryN(i);
            List<List<Double>> lineCoords = new ArrayList<>();

            Coordinate[] coords = lineString.getCoordinates();
            for (Coordinate coord : coords) {
                List<Double> point = new ArrayList<>();
                point.add(coord.x); // longitude
                point.add(coord.y); // latitude
                lineCoords.add(point);
            }
            coordinates.add(lineCoords);
        }

        multiLineString.setCoordinates(coordinates);
        return multiLineString;
    }

    private static usdot.v2x.app.api.models.etx.configuration.geometry.MultiPolygon convertMultiPolygon(
            org.locationtech.jts.geom.MultiPolygon jtsMultiPolygon) {
        usdot.v2x.app.api.models.etx.configuration.geometry.MultiPolygon multiPolygon = new usdot.v2x.app.api.models.etx.configuration.geometry.MultiPolygon();

        List<List<List<List<Double>>>> coordinates = new ArrayList<>();

        for (int i = 0; i < jtsMultiPolygon.getNumGeometries(); i++) {
            org.locationtech.jts.geom.Polygon polygon = (org.locationtech.jts.geom.Polygon) jtsMultiPolygon
                    .getGeometryN(i);
            List<List<List<Double>>> polygonCoords = new ArrayList<>();

            // Add exterior ring
            List<List<Double>> exteriorRing = new ArrayList<>();
            Coordinate[] exteriorCoords = polygon.getExteriorRing().getCoordinates();
            for (Coordinate coord : exteriorCoords) {
                List<Double> point = new ArrayList<>();
                point.add(coord.x); // longitude
                point.add(coord.y); // latitude
                exteriorRing.add(point);
            }
            polygonCoords.add(exteriorRing);

            // Add interior rings (holes)
            for (int j = 0; j < polygon.getNumInteriorRing(); j++) {
                List<List<Double>> interiorRing = new ArrayList<>();
                Coordinate[] interiorCoords = polygon.getInteriorRingN(j).getCoordinates();
                for (Coordinate coord : interiorCoords) {
                    List<Double> point = new ArrayList<>();
                    point.add(coord.x); // longitude
                    point.add(coord.y); // latitude
                    interiorRing.add(point);
                }
                polygonCoords.add(interiorRing);
            }

            coordinates.add(polygonCoords);
        }

        multiPolygon.setCoordinates(coordinates);
        return multiPolygon;
    }

    // Note: Point conversion is not implemented as it's not in your custom geometry
    // types
    private static Geometry convertPoint(org.locationtech.jts.geom.Point jtsPoint) {
        throw new UnsupportedOperationException("Point geometry type is not supported in custom geometry objects");
    }

    // Conversion methods from custom geometry objects to JTS

    private static org.locationtech.jts.geom.LineString convertToJtsLineString(
            usdot.v2x.app.api.models.etx.configuration.geometry.LineString lineString) {
        List<List<Double>> coords = lineString.getCoordinates();
        Coordinate[] coordinates = new Coordinate[coords.size()];

        for (int i = 0; i < coords.size(); i++) {
            List<Double> point = coords.get(i);
            coordinates[i] = new Coordinate(point.get(0), point.get(1)); // lon, lat
        }

        return geometryFactory.createLineString(coordinates);
    }

    private static org.locationtech.jts.geom.Polygon convertToJtsPolygon(
            usdot.v2x.app.api.models.etx.configuration.geometry.Polygon polygon) {
        List<List<List<Double>>> coords = polygon.getCoordinates();

        if (coords.isEmpty()) {
            throw new IllegalArgumentException("Polygon must have at least one ring");
        }

        // Create exterior ring
        List<List<Double>> exteriorRing = coords.get(0);
        Coordinate[] exteriorCoordinates = new Coordinate[exteriorRing.size()];
        for (int i = 0; i < exteriorRing.size(); i++) {
            List<Double> point = exteriorRing.get(i);
            exteriorCoordinates[i] = new Coordinate(point.get(0), point.get(1)); // lon, lat
        }
        LinearRing exterior = geometryFactory.createLinearRing(exteriorCoordinates);

        // Create interior rings (holes)
        LinearRing[] holes = new LinearRing[coords.size() - 1];
        for (int i = 1; i < coords.size(); i++) {
            List<List<Double>> interiorRing = coords.get(i);
            Coordinate[] interiorCoordinates = new Coordinate[interiorRing.size()];
            for (int j = 0; j < interiorRing.size(); j++) {
                List<Double> point = interiorRing.get(j);
                interiorCoordinates[j] = new Coordinate(point.get(0), point.get(1)); // lon, lat
            }
            holes[i - 1] = geometryFactory.createLinearRing(interiorCoordinates);
        }

        return geometryFactory.createPolygon(exterior, holes);
    }

    private static org.locationtech.jts.geom.MultiLineString convertToJtsMultiLineString(
            usdot.v2x.app.api.models.etx.configuration.geometry.MultiLineString multiLineString) {
        List<List<List<Double>>> coords = multiLineString.getCoordinates();
        org.locationtech.jts.geom.LineString[] lineStrings = new org.locationtech.jts.geom.LineString[coords.size()];

        for (int i = 0; i < coords.size(); i++) {
            List<List<Double>> lineCoords = coords.get(i);
            Coordinate[] coordinates = new Coordinate[lineCoords.size()];

            for (int j = 0; j < lineCoords.size(); j++) {
                List<Double> point = lineCoords.get(j);
                coordinates[j] = new Coordinate(point.get(0), point.get(1)); // lon, lat
            }

            lineStrings[i] = geometryFactory.createLineString(coordinates);
        }

        return geometryFactory.createMultiLineString(lineStrings);
    }

    private static org.locationtech.jts.geom.MultiPolygon convertToJtsMultiPolygon(
            usdot.v2x.app.api.models.etx.configuration.geometry.MultiPolygon multiPolygon) {
        List<List<List<List<Double>>>> coords = multiPolygon.getCoordinates();
        org.locationtech.jts.geom.Polygon[] polygons = new org.locationtech.jts.geom.Polygon[coords.size()];

        for (int i = 0; i < coords.size(); i++) {
            List<List<List<Double>>> polygonCoords = coords.get(i);

            if (polygonCoords.isEmpty()) {
                throw new IllegalArgumentException("Polygon must have at least one ring");
            }

            // Create exterior ring
            List<List<Double>> exteriorRing = polygonCoords.get(0);
            Coordinate[] exteriorCoordinates = new Coordinate[exteriorRing.size()];
            for (int j = 0; j < exteriorRing.size(); j++) {
                List<Double> point = exteriorRing.get(j);
                exteriorCoordinates[j] = new Coordinate(point.get(0), point.get(1)); // lon, lat
            }
            LinearRing exterior = geometryFactory.createLinearRing(exteriorCoordinates);

            // Create interior rings (holes)
            LinearRing[] holes = new LinearRing[polygonCoords.size() - 1];
            for (int j = 1; j < polygonCoords.size(); j++) {
                List<List<Double>> interiorRing = polygonCoords.get(j);
                Coordinate[] interiorCoordinates = new Coordinate[interiorRing.size()];
                for (int k = 0; k < interiorRing.size(); k++) {
                    List<Double> point = interiorRing.get(k);
                    interiorCoordinates[k] = new Coordinate(point.get(0), point.get(1)); // lon, lat
                }
                holes[j - 1] = geometryFactory.createLinearRing(interiorCoordinates);
            }

            polygons[i] = geometryFactory.createPolygon(exterior, holes);
        }

        return geometryFactory.createMultiPolygon(polygons);
    }
}