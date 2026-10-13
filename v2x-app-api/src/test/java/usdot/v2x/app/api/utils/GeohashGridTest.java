package usdot.v2x.app.api.utils;

import ch.hsr.geohash.BoundingBox;
import ch.hsr.geohash.GeoHash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeohashGridTest {

    private static final double TOLERANCE = 1e-12;

    @Test
    void stepsMatchGeohashCellDimensionsForEveryPrecision() {
        for (int precision = 1; precision <= 12; precision++) {
            BoundingBox box = GeoHash.withCharacterPrecision(37.0, -122.0, precision).getBoundingBox();
            double latitudeHeight = box.getNorthLatitude() - box.getSouthLatitude();
            double longitudeWidth = box.getEastLongitude() - box.getWestLongitude();

            assertEquals(latitudeHeight, GeohashGrid.latStep(precision), TOLERANCE,
                    "latStep for precision " + precision);
            assertEquals(longitudeWidth, GeohashGrid.lonStep(precision), TOLERANCE,
                    "lonStep for precision " + precision);
        }
    }

    @Test
    void rejectsPrecisionOutsideSupportedRange() {
        assertThrows(IllegalArgumentException.class, () -> GeohashGrid.latStep(0));
        assertThrows(IllegalArgumentException.class, () -> GeohashGrid.latStep(13));
        assertThrows(IllegalArgumentException.class, () -> GeohashGrid.lonStep(0));
        assertThrows(IllegalArgumentException.class, () -> GeohashGrid.lonStep(13));
    }
}
