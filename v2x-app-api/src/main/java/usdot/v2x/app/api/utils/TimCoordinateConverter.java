package usdot.v2x.app.api.utils;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.*;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath.*;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.OffsetSystem.*;
import us.dot.its.jpo.asn.j2735.r2024.Common.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.buffer.BufferParameters;
import java.awt.geom.Point2D;
import org.springframework.stereotype.Component;

import usdot.v2x.app.api.config.GeometryProperties;

@Component
public class TimCoordinateConverter {
    private static final GeometryFactory geometryFactory = GeometryUtils.getGeometryFactory();
    private final GeometryProperties geometryProperties;

    static class GeoPoint {
        final double lat;
        final double lon;

        GeoPoint(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }

    public TimCoordinateConverter(GeometryProperties geometryProperties) {
        this.geometryProperties = geometryProperties;
    }

    public static class TimGeometry {
        public final LineString pathGeometry;
        public final Polygon geofenceGeometry;

        public TimGeometry(LineString pathGeometry, Polygon geofenceGeometry) {
            this.pathGeometry = pathGeometry;
            this.geofenceGeometry = geofenceGeometry;
        }
    }

    public TimGeometry convertTimToCoordinates(List<TravelerDataFrame> dataFrames) {
        List<Coordinate> allPathCoordinates = new ArrayList<>();
        List<Polygon> allPolygons = new ArrayList<>();

        for (TravelerDataFrame dataFrame : dataFrames) {
            List<GeographicalPath> regions = dataFrame.getRegions();
            if (regions != null) {
                for (GeographicalPath region : regions) {
                    List<List<Double>> regionPath = processRegion(region);

                    // Convert region coordinates to JTS Coordinates
                    List<Coordinate> regionCoords = regionPath.stream()
                            .map(point -> new Coordinate(point.get(0), point.get(1)))
                            .collect(Collectors.toList());
                    allPathCoordinates.addAll(regionCoords);

                    Polygon regionPolygon = null;

                    // Check if closedPath is set to true in the region
                    boolean isClosedPath = region.getClosedPath() != null && region.getClosedPath().getValue();

                    // Check if this is a closed region (circle) or has a geometry
                    boolean isClosedRegion = region.getDescription() != null &&
                            region.getDescription().getGeometry() != null &&
                            region.getDescription().getGeometry().getCircle() != null;

                    if (isClosedPath) {
                        // If closedPath is true, treat the offset path as a polygon and apply offset
                        regionPolygon = createGeofencePolygonFromClosedPath(regionPath);
                    } else if (isClosedRegion) {
                        regionPolygon = createGeofencePolygonClosedPath(regionPath);
                    } else {
                        double laneWidth = region.getLaneWidth() != null ? region.getLaneWidth().getValue()
                                : geometryProperties.getDefaultLaneWidthCm();
                        regionPolygon = createGeofencePolygonOpenPath(regionPath, laneWidth);
                    }

                    if (regionPolygon != null) {
                        allPolygons.add(regionPolygon);
                    }
                }
            }
        }

        // Create LineString from all path coordinates
        LineString pathGeometry = geometryFactory.createLineString(
                allPathCoordinates.toArray(new Coordinate[0]));

        // Combine all polygons into a single polygon using union operation
        Polygon combinedPolygon = GeometryUtils.combinePolygons(allPolygons);

        return new TimGeometry(pathGeometry, combinedPolygon);
    }

    private List<List<Double>> processRegion(GeographicalPath region) {
        List<List<Double>> coordinates = new ArrayList<>();

        // Get coordinates from the description
        DescriptionChoice description = region.getDescription();
        if (description != null) {
            if (description.getPath() != null) {
                coordinates.addAll(processOffsetPath(region, description.getPath()));
            } else if (description.getGeometry() != null) {
                coordinates.addAll(processGeometry(region, description.getGeometry()));
            }
        }

        // If no description, try to use anchor point with lane width
        if (coordinates.isEmpty() && region.getAnchor() != null) {
            Position3D anchor = region.getAnchor();
            LaneWidth laneWidth = region.getLaneWidth();
            double width = (laneWidth != null) ? laneWidth.getValue() : geometryProperties.getDefaultLaneWidthCm();
            coordinates.addAll(createRectangleFromAnchor(anchor, width));
        }

        return coordinates;
    }

    private static List<List<Double>> processOffsetPath(GeographicalPath region, OffsetSystem path) {
        List<List<Double>> coordinates = new ArrayList<>();
        Position3D anchor = region.getAnchor();
        if (anchor == null) {
            return coordinates;
        }

        List<GeoPoint> points = new ArrayList<>();
        GeoPoint anchorPoint = new GeoPoint(
                anchor.getLat().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR,
                anchor.getLong_().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR);

        OffsetChoice choice = path.getOffset();
        if (choice != null) {
            if (choice.getXy() != null) {
                // Handle XY offset points
                NodeListXY nodes = choice.getXy();
                GeoPoint prevPoint = anchorPoint;
                for (NodeXY node : nodes.getNodes()) {
                    GeoPoint nextPoint = convertNodeToGeoPoint(node.getDelta(), prevPoint);
                    if (nextPoint != null) {
                        points.add(nextPoint);
                        prevPoint = nextPoint;
                    }
                }
            } else if (choice.getLl() != null) {
                // Handle LL (latitude/longitude) points
                NodeListLL nodes = choice.getLl();
                GeoPoint prevPoint = anchorPoint;
                for (NodeLL node : nodes.getNodes()) {
                    GeoPoint nextPoint = convertLLNodeToGeoPoint(node.getDelta(), prevPoint);
                    if (nextPoint != null) {
                        points.add(nextPoint);
                        prevPoint = nextPoint;
                    }
                }
            }
        }

        coordinates.addAll(points.stream()
                .map(point -> Arrays.asList(point.lon, point.lat))
                .collect(Collectors.toList()));

        return coordinates;
    }

    private static GeoPoint convertNodeToGeoPoint(NodeOffsetPointXY node, GeoPoint prevPoint) {
        if (node.getNode_XY1() != null) {
            Node_XY_20b xy = node.getNode_XY1();
            return offsetGeoPoint(prevPoint, xy.getX().getValue(), xy.getY().getValue());
        } else if (node.getNode_XY2() != null) {
            Node_XY_22b xy = node.getNode_XY2();
            return offsetGeoPoint(prevPoint, xy.getX().getValue(), xy.getY().getValue());
        } else if (node.getNode_XY3() != null) {
            Node_XY_24b xy = node.getNode_XY3();
            return offsetGeoPoint(prevPoint, xy.getX().getValue(), xy.getY().getValue());
        } else if (node.getNode_XY4() != null) {
            Node_XY_26b xy = node.getNode_XY4();
            return offsetGeoPoint(prevPoint, xy.getX().getValue(), xy.getY().getValue());
        } else if (node.getNode_XY5() != null) {
            Node_XY_28b xy = node.getNode_XY5();
            return offsetGeoPoint(prevPoint, xy.getX().getValue(), xy.getY().getValue());
        } else if (node.getNode_XY6() != null) {
            Node_XY_32b xy = node.getNode_XY6();
            return offsetGeoPoint(prevPoint, xy.getX().getValue(), xy.getY().getValue());
        } else if (node.getNode_LatLon() != null) {
            Node_LLmD_64b ll = node.getNode_LatLon();
            return new GeoPoint(
                    ll.getLat().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR,
                    ll.getLon().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR);
        }
        return null;
    }

    private static GeoPoint convertLLNodeToGeoPoint(NodeOffsetPointLL node, GeoPoint prevPoint) {
        if (node == null) {
            return null;
        }

        if (node.getNode_LL1() != null) {
            Node_LL_24B ll = node.getNode_LL1();
            return offsetLatLonGeoPoint(prevPoint, ll.getLon().getValue(), ll.getLat().getValue(),
                    GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR);
        } else if (node.getNode_LL2() != null) {
            Node_LL_28B ll = node.getNode_LL2();
            return offsetLatLonGeoPoint(prevPoint, ll.getLon().getValue(), ll.getLat().getValue(),
                    GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR);
        } else if (node.getNode_LL3() != null) {
            Node_LL_32B ll = node.getNode_LL3();
            return offsetLatLonGeoPoint(prevPoint, ll.getLon().getValue(), ll.getLat().getValue(),
                    GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR);
        } else if (node.getNode_LL4() != null) {
            Node_LL_36B ll = node.getNode_LL4();
            return offsetLatLonGeoPoint(prevPoint, ll.getLon().getValue(), ll.getLat().getValue(),
                    GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR);
        } else if (node.getNode_LL5() != null) {
            Node_LL_44B ll = node.getNode_LL5();
            return offsetLatLonGeoPoint(prevPoint, ll.getLon().getValue(), ll.getLat().getValue(),
                    GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR);
        } else if (node.getNode_LL6() != null) {
            Node_LL_48B ll = node.getNode_LL6();
            return offsetLatLonGeoPoint(prevPoint, ll.getLon().getValue(), ll.getLat().getValue(),
                    GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR);
        } else if (node.getNode_LatLon() != null) {
            Node_LLmD_64b ll = node.getNode_LatLon();
            return new GeoPoint(
                    ll.getLat().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR,
                    ll.getLon().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR);
        }
        return null;
    }

    private static GeoPoint offsetLatLonGeoPoint(GeoPoint prevPoint, long offsetLon, long offsetLat, double scale) {
        // Convert scaled offsets to decimal degrees
        double latOffset = offsetLat / scale;
        double lonOffset = offsetLon / scale;

        return new GeoPoint(
                prevPoint.lat + latOffset,
                prevPoint.lon + lonOffset);
    }

    private static GeoPoint offsetGeoPoint(GeoPoint prevPoint, long offsetX, long offsetY) {
        // Convert centimeter offsets to decimal degrees
        // 1 degree latitude = ~111111 meters = 11111100 centimeters
        double latOffset = offsetY / 11111100.0;
        // 1 degree longitude = ~111111 * cos(lat) meters
        double lonOffset = offsetX / (11111100.0 * Math.cos(Math.toRadians(prevPoint.lat)));

        return new GeoPoint(
                prevPoint.lat + latOffset,
                prevPoint.lon + lonOffset);
    }

    private static List<List<Double>> processGeometry(GeographicalPath region, GeometricProjection geometry) {
        if (geometry.getCircle() != null) {
            return processCircle(geometry.getCircle());
        }
        return new ArrayList<>();
    }

    private static List<List<Double>> processCircle(Circle circle) {
        Position3D center = circle.getCenter();
        double centerLat = center.getLat().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR;
        double centerLon = center.getLong_().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR;
        double radius = circle.getRadius().getValue();

        // Create circle approximation using 32 points
        List<List<Double>> coordinates = new ArrayList<>();
        int numPoints = 16;

        for (int i = 0; i <= numPoints; i++) {
            double angle = 360.0 * i / numPoints; // Angle in degrees
            Point2D destination = GeometryUtils.calculateDestinationPoint(centerLon, centerLat, angle, radius);
            coordinates.add(Arrays.asList(destination.getX(), destination.getY())); // Longitude, Latitude
        }

        return coordinates;
    }

    private static List<List<Double>> createRectangleFromAnchor(Position3D anchor, double width) {
        List<List<Double>> coordinates = new ArrayList<>();
        double lat = anchor.getLat().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR;
        double lon = anchor.getLong_().getValue() / GeometryUtils.J2735_DECIMAL_CONVERSION_FACTOR;
        double padding = 0.005; // GEOFENCE_LAT_LONG_PADDING

        coordinates.add(Arrays.asList(lon - padding, lat - padding));
        coordinates.add(Arrays.asList(lon + padding, lat - padding));
        coordinates.add(Arrays.asList(lon + padding, lat + padding));
        coordinates.add(Arrays.asList(lon - padding, lat + padding));
        coordinates.add(Arrays.asList(lon - padding, lat - padding));

        return coordinates;
    }

    private Polygon createGeofencePolygonOpenPath(List<List<Double>> coordinates, double laneWidthCm) {
        try {
            if (coordinates == null || coordinates.isEmpty()) {
                return null;
            }

            // Convert coordinates to JTS Coordinate array - swap to lat,lon for UTM
            Coordinate[] coords = coordinates.stream()
                    .map(point -> new Coordinate(point.get(1), point.get(0))) // swap to lat,lon for UTM
                    .toArray(Coordinate[]::new);

            // Handle single point case
            if (coords.length == 1) {
                return createPointBufferGeometry(coords[0], geometryProperties.getGeofenceOffsetMeters());
            }

            // Create LineString from coordinates
            LineString lineString = geometryFactory.createLineString(coords);

            // Set buffer parameters for open paths
            BufferParameters bufferParams = GeometryUtils.createBufferParameters(1, BufferParameters.CAP_SQUARE);

            // Apply buffer using lane width plus geofence offset
            double totalBufferDistance = (laneWidthCm / 100.0) + geometryProperties.getGeofenceOffsetMeters();
            return (Polygon) GeometryUtils.bufferGeometryWithUtm(lineString, totalBufferDistance, bufferParams);
        } catch (Exception e) {
            throw new RuntimeException("Error creating geofence polygon", e);
        }
    }

    private Polygon createGeofencePolygonClosedPath(List<List<Double>> coordinates) {
        try {
            if (coordinates == null || coordinates.isEmpty()) {
                return null;
            }

            // Convert coordinates to JTS Coordinate array - swap to lat,lon for UTM
            Coordinate[] coords = coordinates.stream()
                    .map(point -> new Coordinate(point.get(1), point.get(0))) // swap to lat,lon for UTM
                    .toArray(Coordinate[]::new);

            // Handle single point case
            if (coords.length == 1) {
                return createPointBufferGeometry(coords[0], geometryProperties.getGeofenceOffsetMeters());
            }

            // Create LinearRing for closed polygon
            LinearRing ring = geometryFactory.createLinearRing(coords);
            Polygon polygon = geometryFactory.createPolygon(ring);

            // Set buffer parameters for closed paths
            BufferParameters bufferParams = GeometryUtils.createBufferParameters(1, BufferParameters.CAP_SQUARE);
            bufferParams.setJoinStyle(BufferParameters.JOIN_MITRE);

            return (Polygon) GeometryUtils.bufferGeometryWithUtm(polygon, geometryProperties.getGeofenceOffsetMeters(),
                    bufferParams);
        } catch (Exception e) {
            throw new RuntimeException("Error creating closed geofence polygon", e);
        }
    }

    private Polygon createGeofencePolygonFromClosedPath(List<List<Double>> coordinates) {
        try {
            if (coordinates == null || coordinates.isEmpty()) {
                return null;
            }

            // Convert coordinates to JTS Coordinate array - swap to lat,lon for UTM
            List<Coordinate> coordsList = coordinates.stream()
                    .map(point -> new Coordinate(point.get(1), point.get(0))) // swap to lat,lon for UTM
                    .collect(Collectors.toList());

            // Handle single point case
            if (coordsList.size() == 1) {
                return createPointBufferGeometry(coordsList.get(0), geometryProperties.getGeofenceOffsetMeters());
            }

            // Ensure the path is closed by adding the first point at the end if it's not
            // already there
            if (coordsList.size() > 1 && !coordsList.get(0).equals2D(coordsList.get(coordsList.size() - 1))) {
                coordsList.add(new Coordinate(coordsList.get(0)));
            }

            Coordinate[] coords = coordsList.toArray(new Coordinate[0]);

            // Create LinearRing for closed polygon from the offset path
            LinearRing ring = geometryFactory.createLinearRing(coords);
            Polygon polygon = geometryFactory.createPolygon(ring);

            // Apply offset to the polygon using the geofence offset
            BufferParameters bufferParams = GeometryUtils.createBufferParameters(1, BufferParameters.CAP_SQUARE);
            bufferParams.setJoinStyle(BufferParameters.JOIN_MITRE);

            return (Polygon) GeometryUtils.bufferGeometryWithUtm(polygon, geometryProperties.getGeofenceOffsetMeters(),
                    bufferParams);
        } catch (Exception e) {
            throw new RuntimeException("Error creating geofence polygon from closed path", e);
        }
    }

    private static Polygon createPointBufferGeometry(Coordinate point, double radiusMeters) throws Exception {
        Point center = geometryFactory.createPoint(point);

        BufferParameters params = GeometryUtils.createBufferParameters(8, BufferParameters.CAP_ROUND);

        return (Polygon) GeometryUtils.bufferGeometryWithUtm(center, radiusMeters, params);
    }
}