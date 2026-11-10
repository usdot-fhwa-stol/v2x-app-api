package usdot.v2x.app.api.utils;

/**
 * Geohash grid dimensions shared by coverage selection and scan budgeting.
 */
final class GeohashGrid {

    private GeohashGrid() {
    }

    /**
     * Latitude step for a geohash precision. Latitude spans 180° (−90 to +90);
     * with {@code latBits} bits there are 2^latBits rows, so each row is
     * 180° / 2^latBits tall.
     */
    static double latStep(int precision) {
        int bits = bitsFor(precision);
        return 180.0 / Math.pow(2, bits / 2);
    }

    /**
     * Longitude step for a geohash precision. Longitude spans 360° (−180 to +180)
     * and gets the extra bit for odd totals, so each column is 360° / 2^lonBits wide.
     */
    static double lonStep(int precision) {
        int bits = bitsFor(precision);
        return 360.0 / Math.pow(2, (bits + 1) / 2);
    }

    private static int bitsFor(int precision) {
        if (precision < 1 || precision > 12) {
            throw new IllegalArgumentException("Geohash precision must be between 1 and 12");
        }
        return precision * 5;
    }
}
