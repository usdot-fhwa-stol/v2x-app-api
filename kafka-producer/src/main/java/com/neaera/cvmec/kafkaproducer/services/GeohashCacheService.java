package com.neaera.cvmec.kafkaproducer.services;

import com.neaera.cvmec.kafkaproducer.models.postgres.derived.GeohashPayloadMessage;
import com.neaera.cvmec.kafkaproducer.models.GeoHashRoutedMsg;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GeohashCacheService {

    private final Map<String, List<GeoHashRoutedMsg>> geohashCache = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Updates the cache with new geohash payload messages
     * 
     * @param messages List of GeohashPayloadMessage to cache
     */
    public void updateCache(List<GeohashPayloadMessage> messages) {
        lock.writeLock().lock();
        try {
            // Clear existing cache and add new messages
            geohashCache.clear();

            for (GeohashPayloadMessage message : messages) {
                if (message.getGeohash() != null) {
                    // Convert GeohashPayloadMessage to GeoHashRoutedMsg
                    GeoHashRoutedMsg routedMsg = new GeoHashRoutedMsg(
                            message.getHexPayload(),
                            message.getGeohash());
                    geohashCache.computeIfAbsent(message.getGeohash(), k -> new ArrayList<>()).add(routedMsg);
                }
            }

            int totalMessages = geohashCache.values().stream().mapToInt(List::size).sum();
            log.info("Cache updated with {} GeoHashRoutedMsg messages across {} unique geohashes",
                    totalMessages, geohashCache.size());

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Retrieves all geohash routed messages for a specific geohash
     * 
     * @param geohash The geohash key to lookup
     * @return List of GeoHashRoutedMsg for the geohash, or empty list if not found
     */
    public List<GeoHashRoutedMsg> getByGeohash(String geohash) {
        lock.readLock().lock();
        try {
            List<GeoHashRoutedMsg> messages = geohashCache.get(geohash);
            return messages != null ? new ArrayList<>(messages) : Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Retrieves all cached geohash routed messages
     * 
     * @return List of all cached GeoHashRoutedMsg objects
     */
    public List<GeoHashRoutedMsg> getAllMessages() {
        lock.readLock().lock();
        try {
            return geohashCache.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if a geohash exists in the cache
     * 
     * @param geohash The geohash to check
     * @return true if exists, false otherwise
     */
    public boolean containsGeohash(String geohash) {
        lock.readLock().lock();
        try {
            return geohashCache.containsKey(geohash);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the number of cached messages
     * 
     * @return total number of messages in the cache
     */
    public int getCacheSize() {
        lock.readLock().lock();
        try {
            return geohashCache.values().stream().mapToInt(List::size).sum();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the number of unique geohashes in the cache
     * 
     * @return number of unique geohashes
     */
    public int getUniqueGeohashCount() {
        lock.readLock().lock();
        try {
            return geohashCache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clears the entire cache
     */
    public void clearCache() {
        lock.writeLock().lock();
        try {
            geohashCache.clear();
            log.info("Cache cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }
}