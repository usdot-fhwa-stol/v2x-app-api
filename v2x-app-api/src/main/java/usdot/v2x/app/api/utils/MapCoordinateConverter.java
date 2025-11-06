package usdot.v2x.app.api.utils;

import us.dot.its.jpo.asn.j2735.r2024.MapData.GenericLane;
import us.dot.its.jpo.asn.j2735.r2024.Common.*;
import us.dot.its.jpo.asn.j2735.r2024.MapData.IntersectionGeometry;
import us.dot.its.jpo.asn.j2735.r2024.MapData.LaneList;
import us.dot.its.jpo.asn.j2735.r2024.MapData.IntersectionGeometryList;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.buffer.BufferParameters;

import java.awt.geom.Point2D;
import org.springframework.stereotype.Component;

import usdot.v2x.app.api.config.GeometryProperties;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class MapCoordinateConverter {
    private final GeometryProperties geometryProperties;
    private static final GeometryFactory geometryFactory = GeometryUtils.getGeometryFactory();

    public MapCoordinateConverter(GeometryProperties geometryProperties) {
        this.geometryProperties = geometryProperties;
    }

    static class GeoPoint {
        final double lat;
        final double lon;

        GeoPoint(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static class MapGeometry {
        public final Polygon geofenceGeometry;

        public MapGeometry(Polygon geofenceGeometry) {
            this.geofenceGeometry = geofenceGeometry;
        }
    }

    public MapGeometry convertMapToCoordinates(IntersectionGeometryList intersections) {
        if (intersections == null || intersections.isEmpty()) {
            throw new IllegalArgumentException("MAP message must contain at least one intersection");
        }

        List<Polygon> polygons = new ArrayList<>();
        for (IntersectionGeometry intersection : intersections) {
            Polygon intersectionPolygon = convertIntersectionToPolygon(intersection);
            if (intersectionPolygon != null) {
                polygons.add(intersectionPolygon);
            }
        }

        if (polygons.isEmpty()) {
            throw new IllegalArgumentException("No valid intersections found in MAP message");
        }

        // Combine all polygons into a single polygon using union operation
        Polygon combinedPolygon = GeometryUtils.combinePolygons(polygons);

        return new MapGeometry(combinedPolygon);
    }

    private Polygon convertIntersectionToPolygon(IntersectionGeometry intersection) {
        Position3D refPoint = intersection.getRefPoint();
        if (refPoint == null) {
            return null;
        }

        // Convert reference point to decimal degrees
        double refLat = refPoint.getLat().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR;
        double refLon = refPoint.getLong_().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR;
        GeoPoint refGeoPoint = new GeoPoint(refLat, refLon);

        // Find the bounds of all lane nodes
        double minLat = refLat;
        double maxLat = refLat;
        double minLon = refLon;
        double maxLon = refLon;

        LaneList laneSet = intersection.getLaneSet();
        if (laneSet != null) {
            for (GenericLane lane : laneSet) {
                NodeListXY nodeList = lane.getNodeList();
                if (nodeList != null && nodeList.getNodes() != null) {
                    GeoPoint prevPoint = refGeoPoint;
                    for (NodeXY node : nodeList.getNodes()) {
                        GeoPoint nodePoint = convertNodeToGeoPoint(node.getDelta(), prevPoint);
                        if (nodePoint != null) {
                            minLat = Math.min(minLat, nodePoint.lat);
                            maxLat = Math.max(maxLat, nodePoint.lat);
                            minLon = Math.min(minLon, nodePoint.lon);
                            maxLon = Math.max(maxLon, nodePoint.lon);
                            prevPoint = nodePoint;
                        }
                    }
                }
            }
        }

        try {
            // Create a polygon from the bounds - NOTE: JTS expects (lat, lon) order for
            // WGS84
            Coordinate[] coords = new Coordinate[] {
                    new Coordinate(minLat, minLon), // Bottom left
                    new Coordinate(maxLat, minLon), // Top left
                    new Coordinate(maxLat, maxLon), // Top right
                    new Coordinate(minLat, maxLon), // Bottom right
                    new Coordinate(minLat, minLon) // Close the polygon
            };
            LinearRing ring = geometryFactory.createLinearRing(coords);
            Polygon boundingBox = geometryFactory.createPolygon(ring);

            // Add a buffer around the bounds
            BufferParameters bufferParams = GeometryUtils.createBufferParameters(1, BufferParameters.CAP_SQUARE);
            bufferParams.setJoinStyle(BufferParameters.JOIN_MITRE);

            return (Polygon) GeometryUtils.bufferGeometryWithUtm(boundingBox,
                    geometryProperties.getGeofenceOffsetMeters(), bufferParams);
        } catch (Exception e) {
            throw new RuntimeException("Error creating bounding region", e);
        }
    }

    private static GeoPoint convertNodeToGeoPoint(NodeOffsetPointXY node, GeoPoint prevPoint) {
        if (node.getNode_XY1() != null) {
            Node_XY_20b xy = node.getNode_XY1();
            return calculateOffsetPoint(prevPoint, xy.getX().getValue(), xy.getY().getValue());
        } else if (node.getNode_XY2() != null) {
            Node_XY_22b xy = node.getNode_XY2();
            return calculateOffsetPoint(prevPoint, xy.getX().getValue(), xy.getY().getValue());
        } else if (node.getNode_XY3() != null) {
            Node_XY_24b xy = node.getNode_XY3();
            return calculateOffsetPoint(prevPoint, xy.getX().getValue(), xy.getY().getValue());
        } else if (node.getNode_XY4() != null) {
            Node_XY_26b xy = node.getNode_XY4();
            return calculateOffsetPoint(prevPoint, xy.getX().getValue(), xy.getY().getValue());
        } else if (node.getNode_XY5() != null) {
            Node_XY_28b xy = node.getNode_XY5();
            return calculateOffsetPoint(prevPoint, xy.getX().getValue(), xy.getY().getValue());
        } else if (node.getNode_XY6() != null) {
            Node_XY_32b xy = node.getNode_XY6();
            return calculateOffsetPoint(prevPoint, xy.getX().getValue(), xy.getY().getValue());
        } else if (node.getNode_LatLon() != null) {
            Node_LLmD_64b ll = node.getNode_LatLon();
            return new GeoPoint(
                    ll.getLat().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR,
                    ll.getLon().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR);
        }
        return null;
    }

    private static GeoPoint calculateOffsetPoint(GeoPoint prevPoint, double offsetX, double offsetY) {
        // Convert offsets from centimeters to meters
        double offsetXMeters = offsetX * 0.01;
        double offsetYMeters = offsetY * 0.01;

        // First calculate the east-west point (90 degrees)
        Point2D eastWestPoint = GeometryUtils.calculateDestinationPoint(prevPoint.lon, prevPoint.lat, 90,
                offsetXMeters);

        // Then calculate the final point with north-south offset (0 degrees)
        Point2D finalPoint = GeometryUtils.calculateDestinationPoint(eastWestPoint.getX(), eastWestPoint.getY(), 0,
                offsetYMeters);

        return new GeoPoint(finalPoint.getY(), finalPoint.getX()); // Convert back to (lat,lon) order
    }
}