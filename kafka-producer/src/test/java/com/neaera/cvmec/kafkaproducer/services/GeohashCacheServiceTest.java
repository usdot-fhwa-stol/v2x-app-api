package com.neaera.cvmec.kafkaproducer.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.neaera.cvmec.kafkaproducer.models.GeoHashRoutedMsg;
import com.neaera.cvmec.kafkaproducer.models.postgres.derived.GeohashPayloadMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeohashCacheServiceTest {

    @Test
    void updateCache_populatesCacheGroupedByGeohash() {
        GeohashCacheService cacheService = new GeohashCacheService();

        cacheService.updateCache(List.of(
                new GeohashPayloadMessage("9q8yy", "0102"),
                new GeohashPayloadMessage("9q8yy", "0304"),
                new GeohashPayloadMessage("9q8yz", "0506")));

        assertEquals(3, cacheService.getCacheSize());
        assertEquals(2, cacheService.getUniqueGeohashCount());
        assertTrue(cacheService.containsGeohash("9q8yy"));
        assertEquals(2, cacheService.getByGeohash("9q8yy").size());
        assertEquals(1, cacheService.getByGeohash("9q8yz").size());
        assertTrue(cacheService.getByGeohash("missing").isEmpty());
    }

    @Test
    void updateCache_ignoresMessagesWithNullGeohash() {
        GeohashCacheService cacheService = new GeohashCacheService();

        cacheService.updateCache(List.of(new GeohashPayloadMessage(null, "0102")));

        assertEquals(0, cacheService.getCacheSize());
    }

    @Test
    void updateCache_replacesPreviousSnapshotEntirely() {
        GeohashCacheService cacheService = new GeohashCacheService();
        cacheService.updateCache(List.of(new GeohashPayloadMessage("9q8yy", "0102")));

        cacheService.updateCache(List.of(new GeohashPayloadMessage("9q8yz", "0506")));

        assertFalse(cacheService.containsGeohash("9q8yy"));
        assertTrue(cacheService.containsGeohash("9q8yz"));
        assertEquals(1, cacheService.getUniqueGeohashCount());
    }

    @Test
    void getAllMessages_returnsAllCachedMessagesAcrossGeohashes() {
        GeohashCacheService cacheService = new GeohashCacheService();
        cacheService.updateCache(List.of(
                new GeohashPayloadMessage("9q8yy", "0102"),
                new GeohashPayloadMessage("9q8yz", "0506")));

        List<GeoHashRoutedMsg> allMessages = cacheService.getAllMessages();

        assertEquals(2, allMessages.size());
    }

    @Test
    void clearCache_removesAllEntries() {
        GeohashCacheService cacheService = new GeohashCacheService();
        cacheService.updateCache(List.of(new GeohashPayloadMessage("9q8yy", "0102")));

        cacheService.clearCache();

        assertEquals(0, cacheService.getCacheSize());
        assertEquals(0, cacheService.getUniqueGeohashCount());
    }
}
