package usdot.v2x.app.api.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

class GeometryUtilsCombinePolygonsTest {

    private final GeometryFactory geometryFactory = GeometryUtils.getGeometryFactory();

    @Test
    void disconnectedPolygonsRemainAMultiPolygonWithoutRecursion() {
        Polygon west = rectangle(-105.0, 40.0, -104.9, 40.1);
        Polygon east = rectangle(-103.0, 40.0, -102.9, 40.1);

        Geometry combined = GeometryUtils.combinePolygons(List.of(west, east));

        MultiPolygon multiPolygon = assertInstanceOf(MultiPolygon.class, combined);
        assertEquals(2, multiPolygon.getNumGeometries());
        assertTrue(multiPolygon.covers(west));
        assertTrue(multiPolygon.covers(east));
    }

    @Test
    void overlappingPolygonsStillProduceAPolygon() {
        Polygon first = rectangle(-105.0, 40.0, -104.8, 40.2);
        Polygon second = rectangle(-104.9, 40.1, -104.7, 40.3);

        Geometry combined = GeometryUtils.combinePolygons(List.of(first, second));

        assertInstanceOf(Polygon.class, combined);
        assertTrue(combined.covers(first));
        assertTrue(combined.covers(second));
    }

    private Polygon rectangle(double west, double south, double east, double north) {
        return geometryFactory.createPolygon(new Coordinate[] {
                new Coordinate(west, south), new Coordinate(east, south),
                new Coordinate(east, north), new Coordinate(west, north),
                new Coordinate(west, south)
        });
    }
}
