package com.neaera.cvmec.kafkaproducer.services;

import com.neaera.cvmec.kafkaproducer.models.postgres.derived.GeohashPayloadMessage;
import com.neaera.cvmec.kafkaproducer.models.GeoHashRoutedMsg;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GeohashCacheService {

    private final AtomicReference<Map<String, List<GeoHashRoutedMsg>>> geohashCache =
            new AtomicReference<>(Collections.emptyMap());

    /**
     * Replaces the cache with new geohash payload messages. The swap is atomic: readers
     * always see either the previous snapshot or the fully-built new one, never a
     * partially-populated cache.
     *
     * @param messages List of GeohashPayloadMessage to cache
     */
    public void updateCache(List<GeohashPayloadMessage> messages) {
        Map<String, List<GeoHashRoutedMsg>> newCache = new HashMap<>();

        for (GeohashPayloadMessage message : messages) {
            if (message.getGeohash() != null) {
                GeoHashRoutedMsg routedMsg = new GeoHashRoutedMsg(
                        message.getHexPayload(),
                        message.getGeohash());
                newCache.computeIfAbsent(message.getGeohash(), k -> new ArrayList<>()).add(routedMsg);
            }
        }

        geohashCache.set(Collections.unmodifiableMap(newCache));

        int totalMessages = newCache.values().stream().mapToInt(List::size).sum();
        log.info("Cache updated with {} GeoHashRoutedMsg messages across {} unique geohashes",
                totalMessages, newCache.size());
    }

    /**
     * Retrieves all geohash routed messages for a specific geohash
     * 
     * @param geohash The geohash key to lookup
     * @return List of GeoHashRoutedMsg for the geohash, or empty list if not found
     */
    public List<GeoHashRoutedMsg> getByGeohash(String geohash) {
        List<GeoHashRoutedMsg> messages = geohashCache.get().get(geohash);
        return messages != null ? new ArrayList<>(messages) : Collections.emptyList();
    }

    /**
     * Retrieves all cached geohash routed messages
     * 
     * @return List of all cached GeoHashRoutedMsg objects
     */
    public List<GeoHashRoutedMsg> getAllMessages() {
        return geohashCache.get().values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * Checks if a geohash exists in the cache
     * 
     * @param geohash The geohash to check
     * @return true if exists, false otherwise
     */
    public boolean containsGeohash(String geohash) {
        return geohashCache.get().containsKey(geohash);
    }

    /**
     * Returns the number of cached messages
     * 
     * @return total number of messages in the cache
     */
    public int getCacheSize() {
        return geohashCache.get().values().stream().mapToInt(List::size).sum();
    }

    /**
     * Returns the number of unique geohashes in the cache
     * 
     * @return number of unique geohashes
     */
    public int getUniqueGeohashCount() {
        return geohashCache.get().size();
    }

    /**
     * Clears the entire cache
     */
    public void clearCache() {
        geohashCache.set(Collections.emptyMap());
        log.info("Cache cleared");
    }
}
