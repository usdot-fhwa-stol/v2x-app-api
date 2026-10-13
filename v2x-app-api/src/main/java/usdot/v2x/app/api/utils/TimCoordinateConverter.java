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

import lombok.extern.slf4j.Slf4j;
import usdot.v2x.app.api.config.GeometryProperties;

@Slf4j
@Component
public class TimCoordinateConverter {
    private static final GeometryFactory geometryFactory = GeometryUtils.getGeometryFactory();
    private final GeometryProperties geometryProperties;

    enum PathLengthExtension {
        NONE, START, END, BOTH
    }

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
        public final Geometry geofenceGeometry;

        public TimGeometry(LineString pathGeometry, Geometry geofenceGeometry) {
            this.pathGeometry = pathGeometry;
            this.geofenceGeometry = geofenceGeometry;
        }
    }

    public TimGeometry convertTimToCoordinates(List<TravelerDataFrame> dataFrames) {
        List<Coordinate> allPathCoordinates = new ArrayList<>();
        List<Polygon> allPolygons = new ArrayList<>();
        List<RegionWork> regionWork = new ArrayList<>();

        for (TravelerDataFrame dataFrame : dataFrames) {
            List<GeographicalPath> regions = dataFrame.getRegions();
            if (regions != null) {
                for (GeographicalPath region : regions) {
                    List<List<Double>> regionPath = processRegion(region);

                    List<Coordinate> regionCoords = regionPath.stream()
                            .map(point -> new Coordinate(point.get(0), point.get(1)))
                            .collect(Collectors.toList());
                    allPathCoordinates.addAll(regionCoords);

                    boolean isClosedPath = region.getClosedPath() != null && region.getClosedPath().getValue();
                    boolean isClosedRegion = region.getDescription() != null &&
                            region.getDescription().getGeometry() != null &&
                            region.getDescription().getGeometry().getCircle() != null;
                    boolean isOpenPath = !isClosedPath && !isClosedRegion;
                    regionWork.add(new RegionWork(region, regionPath, isClosedPath, isClosedRegion, isOpenPath));
                }
            }
        }

        int firstOpenIndex = -1;
        int lastOpenIndex = -1;
        for (int i = 0; i < regionWork.size(); i++) {
            if (regionWork.get(i).isOpenPath) {
                if (firstOpenIndex < 0) {
                    firstOpenIndex = i;
                }
                lastOpenIndex = i;
            }
        }

        for (int i = 0; i < regionWork.size(); i++) {
            RegionWork work = regionWork.get(i);
            Polygon regionPolygon = null;
            Geometry regionGeometry = null;

            if (work.isClosedPath) {
                regionPolygon = createGeofencePolygonFromClosedPath(work.path);
            } else if (work.isClosedRegion) {
                regionPolygon = createGeofencePolygonClosedPath(work.path);
            } else {
                double laneWidth = work.region.getLaneWidth() != null ? work.region.getLaneWidth().getValue()
                        : geometryProperties.getDefaultLaneWidthCm();
                PathLengthExtension extension = pathLengthExtensionFor(i, firstOpenIndex, lastOpenIndex);
                if (extension != PathLengthExtension.NONE
                        && geometryProperties.getGeofencePathLengthOffsetMeters() > 0) {
                    log.debug("Applying triangular path extension of {} m (tip width {} m) to {} open-path region {}",
                            geometryProperties.getGeofencePathLengthOffsetMeters(),
                            geometryProperties.getGeofencePathExtensionTipWidthMeters(), extension, i);
                }
                regionGeometry = createGeofencePolygonOpenPath(work.path, laneWidth, extension);
            }

            if (regionPolygon != null) {
                allPolygons.add(regionPolygon);
            } else if (regionGeometry != null) {
                addPolygons(allPolygons, regionGeometry);
            }
        }

        LineString pathGeometry = geometryFactory.createLineString(
                allPathCoordinates.toArray(new Coordinate[0]));

        Geometry combinedGeometry = GeometryUtils.combinePolygons(allPolygons);

        return new TimGeometry(pathGeometry, combinedGeometry);
    }

    static PathLengthExtension pathLengthExtensionFor(int index, int firstOpenIndex, int lastOpenIndex) {
        if (index < 0 || index != firstOpenIndex && index != lastOpenIndex) {
            return PathLengthExtension.NONE;
        }
        if (index == firstOpenIndex && index == lastOpenIndex) {
            return PathLengthExtension.BOTH;
        }
        if (index == firstOpenIndex) {
            return PathLengthExtension.START;
        }
        return PathLengthExtension.END;
    }

    private record RegionWork(GeographicalPath region, List<List<Double>> path, boolean isClosedPath,
            boolean isClosedRegion, boolean isOpenPath) {
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

    private static void addPolygons(List<Polygon> allPolygons, Geometry geometry) {
        if (geometry instanceof Polygon polygon) {
            allPolygons.add(polygon);
            return;
        }
        if (geometry instanceof MultiPolygon multiPolygon) {
            for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                allPolygons.add((Polygon) multiPolygon.getGeometryN(i));
            }
        }
    }

    private Geometry createGeofencePolygonOpenPath(List<List<Double>> coordinates, double laneWidthCm,
            PathLengthExtension extension) {
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

            double lengthOffsetMeters = geometryProperties.getGeofencePathLengthOffsetMeters();
            double tipWidthMeters = geometryProperties.getGeofencePathExtensionTipWidthMeters();
            double corridorHalfWidth = (laneWidthCm / 100.0) + geometryProperties.getGeofenceOffsetMeters();
            boolean hasTriangularExtension = extension != PathLengthExtension.NONE && lengthOffsetMeters > 0;

            int capStyle = hasTriangularExtension ? BufferParameters.CAP_FLAT : BufferParameters.CAP_SQUARE;
            BufferParameters bufferParams = GeometryUtils.createBufferParameters(1, capStyle);

            LineString lineString = geometryFactory.createLineString(coords);
            Geometry corridor = GeometryUtils.bufferGeometryWithUtm(lineString, corridorHalfWidth, bufferParams);

            if (!hasTriangularExtension) {
                return corridor;
            }

            List<Polygon> parts = new ArrayList<>();
            addPolygons(parts, corridor);

            if (extension == PathLengthExtension.START || extension == PathLengthExtension.BOTH) {
                Coordinate start = coords[0];
                Coordinate startNext = findNextDistinctCoordinate(coords, 0, 1);
                if (startNext != null) {
                    parts.add(GeometryUtils.buildTriangularExtensionWedgeWithUtm(start, startNext, corridorHalfWidth,
                            lengthOffsetMeters, tipWidthMeters));
                }
            }

            if (extension == PathLengthExtension.END || extension == PathLengthExtension.BOTH) {
                Coordinate end = coords[coords.length - 1];
                Coordinate endPrev = findNextDistinctCoordinate(coords, coords.length - 1, -1);
                if (endPrev != null) {
                    parts.add(GeometryUtils.buildTriangularExtensionWedgeWithUtm(end, endPrev, corridorHalfWidth,
                            lengthOffsetMeters, tipWidthMeters));
                }
            }

            Geometry combined = GeometryUtils.combinePolygons(parts);
            return combined;
        } catch (Exception e) {
            throw new RuntimeException("Error creating geofence polygon", e);
        }
    }

    /**
     * Builds a wedge polygon extending from a path endpoint. Coordinates are in
     * (lat, lon) order to match the UTM buffer input convention.
     */
    static Polygon buildTriangularExtensionWedge(Coordinate endpoint, Coordinate pathNeighbor,
            double corridorHalfWidth, double lengthOffsetMeters, double tipWidthMeters) {
        try {
            return GeometryUtils.buildTriangularExtensionWedgeWithUtm(endpoint, pathNeighbor, corridorHalfWidth,
                    lengthOffsetMeters, tipWidthMeters);
        } catch (Exception e) {
            throw new RuntimeException("Error building triangular extension wedge", e);
        }
    }

    /**
     * Extends the outer end(s) of an open path. Coordinates are in (lat, lon)
     * order to match the UTM buffer input convention. Middle regions should pass
     * {@link PathLengthExtension#NONE}.
     */
    static Coordinate[] extendOpenPathLength(Coordinate[] coords, double lengthOffsetMeters) {
        return extendOpenPathLength(coords, lengthOffsetMeters, PathLengthExtension.BOTH);
    }

    static Coordinate[] extendOpenPathLength(Coordinate[] coords, double lengthOffsetMeters,
            PathLengthExtension extension) {
        if (lengthOffsetMeters <= 0 || coords.length < 2 || extension == PathLengthExtension.NONE) {
            return coords;
        }

        List<Coordinate> extended = new ArrayList<>();

        if (extension == PathLengthExtension.START || extension == PathLengthExtension.BOTH) {
            Coordinate start = coords[0];
            Coordinate startNext = findNextDistinctCoordinate(coords, 0, 1);
            if (startNext != null) {
                double reverseBearing = GeometryUtils.calculateBearing(startNext.y, startNext.x, start.y, start.x);
                List<Coordinate> prefix = buildExtension(start, reverseBearing, lengthOffsetMeters);
                for (int i = prefix.size() - 1; i >= 0; i--) {
                    extended.add(prefix.get(i));
                }
            }
        }

        extended.addAll(Arrays.asList(coords));

        if (extension == PathLengthExtension.END || extension == PathLengthExtension.BOTH) {
            Coordinate end = coords[coords.length - 1];
            Coordinate endPrev = findNextDistinctCoordinate(coords, coords.length - 1, -1);
            if (endPrev != null) {
                double endBearing = GeometryUtils.calculateBearing(endPrev.y, endPrev.x, end.y, end.x);
                extended.addAll(buildExtension(end, endBearing, lengthOffsetMeters));
            }
        }

        return extended.toArray(new Coordinate[0]);
    }

    private static List<Coordinate> buildExtension(Coordinate origin, double bearingDegrees, double distanceMeters) {
        Point2D destination = GeometryUtils.calculateDestinationPoint(origin.y, origin.x, bearingDegrees,
                distanceMeters);
        return List.of(new Coordinate(destination.getY(), destination.getX()));
    }

    private static Coordinate findNextDistinctCoordinate(Coordinate[] coords, int fromIndex, int step) {
        Coordinate origin = coords[fromIndex];
        for (int i = fromIndex + step; i >= 0 && i < coords.length; i += step) {
            if (!coords[i].equals2D(origin)) {
                return coords[i];
            }
        }
        return null;
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