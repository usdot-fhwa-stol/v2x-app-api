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
        if (precision < 1 || precision > 12) {
            throw new IllegalArgumentException("Geohash precision must be between 1 and 12");
        }

        int bits = precision * 5;
        double latStep = 180.0 / Math.pow(2, bits / 2);
        double lonStep = 360.0 / Math.pow(2, (bits + 1) / 2);

        long south = gridIndex(boundingBox.getSouthLatitude(), 90.0, latStep, 90.0);
        long north = gridIndex(boundingBox.getNorthLatitude(), 90.0, latStep, 90.0);
        long west = gridIndex(boundingBox.getWestLongitude(), 180.0, lonStep, 180.0);
        long east = gridIndex(boundingBox.getEastLongitude(), 180.0, lonStep, 180.0);

        long rows = Math.max(1, north - south + 1);
        long columns = Math.max(1, east - west + 1);
        return saturatedMultiply(rows, columns);
    }

    private static long gridIndex(double coordinate, double origin, double step, double upperBound) {
        double bounded = coordinate >= upperBound ? Math.nextDown(upperBound) : coordinate;
        return (long) Math.floor((bounded + origin) / step);
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
