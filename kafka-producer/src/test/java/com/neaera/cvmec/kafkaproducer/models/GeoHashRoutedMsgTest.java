package com.neaera.cvmec.kafkaproducer.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeoHashRoutedMsgTest {

    @Test
    void toLogDescription_includesGeohashPayloadSizeAndHexPreview() {
        GeoHashRoutedMsg message = new GeoHashRoutedMsg("0102ff", "9q8yy");

        assertEquals("geohash=9q8yy, payloadBytes=3, payloadHex=0102ff", message.toLogDescription());
    }

    @Test
    void formatHexPreview_truncatesLargePayloads() {
        byte[] bytes = new byte[100];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }

        String preview = GeoHashRoutedMsg.formatHexPreview(bytes);

        assertTrue(preview.endsWith("..."));
        assertEquals(131, preview.length());
    }
}
