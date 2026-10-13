package usdot.v2x.app.api.utils;

import ch.hsr.geohash.BoundingBox;
import usdot.v2x.app.api.exceptions.GeofenceTooLargeException;

/**
 * Precision-aware work budget for geohash bounding-envelope scans.
 *
 * <p>The reservation happens before iterator construction, so a request that is
 * clearly too large never allocates or intersects each candidate geohash cell.
 */
final class GeohashScanBudget {

    private final long limit;
    private long reserved;

    GeohashScanBudget(long limit) {
        if (limit <= 0) {
            throw new IllegalStateException("geofence.limits.max-candidate-cells must be positive");
        }
        this.limit = limit;
    }

    long reserve(BoundingBox boundingBox, int precision) {
        long candidateCells = estimateCandidateCellCount(boundingBox, precision);
        long attempted = saturatedAdd(reserved, candidateCells);
        if (attempted > limit) {
            throw new GeofenceTooLargeException(
                    "Deployment region is too large: precision-" + precision
                            + " coverage requires scanning " + attempted
                            + " candidate cells, exceeding the configured maximum of " + limit
                            + " (geofence.limits.max-candidate-cells)");
        }
        reserved = attempted;
        return reserved;
    }

    static long estimateCandidateCellCount(BoundingBox boundingBox, int precision) {
        if (boundingBox == null) {
            return 0;
        }
        CellRange range = cellRange(boundingBox, precision);
        long rows = Math.max(1, range.northRow() - range.southRow() + 1);
        long columns = Math.max(1, range.eastColumn() - range.westColumn() + 1);
        return saturatedMultiply(rows, columns);
    }

    static CellRange cellRange(BoundingBox boundingBox, int precision) {
        double latStep = GeohashGrid.latStep(precision);
        double lonStep = GeohashGrid.lonStep(precision);

        long south = gridIndex(boundingBox.getSouthLatitude(), 90.0, latStep, 90.0);
        long north = gridIndex(boundingBox.getNorthLatitude(), 90.0, latStep, 90.0);
        long west = gridIndex(boundingBox.getWestLongitude(), 180.0, lonStep, 180.0);
        long east = gridIndex(boundingBox.getEastLongitude(), 180.0, lonStep, 180.0);

        return new CellRange(south, north, west, east, latStep, lonStep);
    }

    record CellRange(long southRow, long northRow, long westColumn, long eastColumn,
            double latitudeStep, double longitudeStep) {
    }

    private static long gridIndex(double coordinate, double origin, double step, double upperBound) {
        long lastIndex = (long) ((upperBound + origin) / step) - 1;
        // Adding the origin can round nextDown(upperBound) back to the upper
        // boundary. Clamp the index itself so world edges reserve real cells only.
        return Math.max(0, Math.min(lastIndex, (long) Math.floor((coordinate + origin) / step)));
    }

    private static long saturatedAdd(long left, long right) {
        if (right > Long.MAX_VALUE - left) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left != 0 && right > Long.MAX_VALUE / left) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }
}
