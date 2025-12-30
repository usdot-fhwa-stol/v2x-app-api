package usdot.v2x.georouter.subscription;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import usdot.v2x.georouter.geospatial.GeohashRouter;
import usdot.v2x.georouter.models.SubscriberInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages subscriber registrations and matches messages to subscribers
 * based on geographic relevance.
 */
@Slf4j
@Service
public class SubscriptionManager {

    private final GeohashRouter geohashRouter;

    // Map of client ID to subscriber info
    private final Map<String, SubscriberInfo> subscribers = new ConcurrentHashMap<>();

    // Index: geohash -> set of client IDs
    private final Map<String, Set<String>> geohashIndex = new ConcurrentHashMap<>();

    // Index: message type -> set of client IDs
    private final Map<String, Set<String>> messageTypeIndex = new ConcurrentHashMap<>();

    public SubscriptionManager(GeohashRouter geohashRouter) {
        this.geohashRouter = geohashRouter;
    }

    /**
     * Registers a new subscriber.
     * 
     * @param subscriber Subscriber information
     */
    public void registerSubscriber(SubscriberInfo subscriber) {
        subscribers.put(subscriber.getClientId(), subscriber);

        // Update geohash index
        if (subscriber.getGeohashes() != null) {
            for (String geohash : subscriber.getGeohashes()) {
                geohashIndex.computeIfAbsent(geohash, k -> ConcurrentHashMap.newKeySet())
                        .add(subscriber.getClientId());
            }
        }

        // Update message type index
        if (subscriber.getMessageTypes() != null && !subscriber.getMessageTypes().isEmpty()) {
            for (String messageType : subscriber.getMessageTypes()) {
                messageTypeIndex.computeIfAbsent(messageType, k -> ConcurrentHashMap.newKeySet())
                        .add(subscriber.getClientId());
            }
        } else {
            // Empty message types means all types
            messageTypeIndex.computeIfAbsent("*", k -> ConcurrentHashMap.newKeySet())
                    .add(subscriber.getClientId());
        }

        log.info("Registered subscriber: {} with {} geohashes and {} message types",
                subscriber.getClientId(),
                subscriber.getGeohashes() != null ? subscriber.getGeohashes().size() : 0,
                subscriber.getMessageTypes() != null ? subscriber.getMessageTypes().size() : 0);
    }

    /**
     * Unregisters a subscriber.
     * 
     * @param clientId Client ID of the subscriber to remove
     */
    public void unregisterSubscriber(String clientId) {
        SubscriberInfo subscriber = subscribers.remove(clientId);
        if (subscriber == null) {
            log.warn("Attempted to unregister unknown subscriber: {}", clientId);
            return;
        }

        // Remove from geohash index
        if (subscriber.getGeohashes() != null) {
            for (String geohash : subscriber.getGeohashes()) {
                Set<String> clients = geohashIndex.get(geohash);
                if (clients != null) {
                    clients.remove(clientId);
                    if (clients.isEmpty()) {
                        geohashIndex.remove(geohash);
                    }
                }
            }
        }

        // Remove from message type index
        if (subscriber.getMessageTypes() != null && !subscriber.getMessageTypes().isEmpty()) {
            for (String messageType : subscriber.getMessageTypes()) {
                Set<String> clients = messageTypeIndex.get(messageType);
                if (clients != null) {
                    clients.remove(clientId);
                    if (clients.isEmpty()) {
                        messageTypeIndex.remove(messageType);
                    }
                }
            }
        } else {
            Set<String> clients = messageTypeIndex.get("*");
            if (clients != null) {
                clients.remove(clientId);
                if (clients.isEmpty()) {
                    messageTypeIndex.remove("*");
                }
            }
        }

        log.info("Unregistered subscriber: {}", clientId);
    }

    /**
     * Updates a subscriber's geographic interests.
     * 
     * @param clientId  Client ID
     * @param geohashes New set of geohashes
     */
    public void updateSubscriberGeohashes(String clientId, Set<String> geohashes) {
        SubscriberInfo subscriber = subscribers.get(clientId);
        if (subscriber == null) {
            log.warn("Attempted to update unknown subscriber: {}", clientId);
            return;
        }

        // Remove old geohashes from index
        if (subscriber.getGeohashes() != null) {
            for (String geohash : subscriber.getGeohashes()) {
                Set<String> clients = geohashIndex.get(geohash);
                if (clients != null) {
                    clients.remove(clientId);
                    if (clients.isEmpty()) {
                        geohashIndex.remove(geohash);
                    }
                }
            }
        }

        // Update subscriber
        subscriber.setGeohashes(geohashes);

        // Add new geohashes to index
        if (geohashes != null) {
            for (String geohash : geohashes) {
                geohashIndex.computeIfAbsent(geohash, k -> ConcurrentHashMap.newKeySet())
                        .add(clientId);
            }
        }

        log.info("Updated geohashes for subscriber: {} to {} geohashes",
                clientId, geohashes != null ? geohashes.size() : 0);
    }

    /**
     * Finds all subscribers that match the given geohashes and message type.
     * 
     * @param relevantGeohashes Set of geohashes from the message
     * @param messageType       Message type (e.g., "BSM", "TIM")
     * @return Set of matching subscribers
     */
    public Set<SubscriberInfo> findMatchingSubscribers(Set<String> relevantGeohashes, String messageType) {
        Set<SubscriberInfo> matchingSubscribers = new HashSet<>();

        // Find subscribers by geohash
        Set<String> candidateClientIds = new HashSet<>();
        for (String geohash : relevantGeohashes) {
            // Check exact matches
            Set<String> exactMatches = geohashIndex.get(geohash);
            if (exactMatches != null) {
                candidateClientIds.addAll(exactMatches);
            }

            // Check prefix matches (subscribers subscribed to larger areas)
            for (Map.Entry<String, Set<String>> entry : geohashIndex.entrySet()) {
                String indexedGeohash = entry.getKey();
                if (geohash.startsWith(indexedGeohash)) {
                    candidateClientIds.addAll(entry.getValue());
                }
            }

            // Check reverse prefix matches (subscribers subscribed to smaller areas)
            for (Map.Entry<String, Set<String>> entry : geohashIndex.entrySet()) {
                String indexedGeohash = entry.getKey();
                if (indexedGeohash.startsWith(geohash)) {
                    candidateClientIds.addAll(entry.getValue());
                }
            }
        }

        // Filter by message type
        Set<String> messageTypeClients = new HashSet<>();
        if (messageType != null) {
            Set<String> typeClients = messageTypeIndex.get(messageType);
            if (typeClients != null) {
                messageTypeClients.addAll(typeClients);
            }
        }

        // Also include subscribers that accept all message types
        Set<String> allTypesClients = messageTypeIndex.get("*");
        if (allTypesClients != null) {
            messageTypeClients.addAll(allTypesClients);
        }

        // Intersect geohash matches with message type matches
        candidateClientIds.retainAll(messageTypeClients);

        // Build result set
        for (String clientId : candidateClientIds) {
            SubscriberInfo subscriber = subscribers.get(clientId);
            if (subscriber != null) {
                matchingSubscribers.add(subscriber);
            }
        }

        return matchingSubscribers;
    }

    /**
     * Gets all registered subscribers.
     */
    public Collection<SubscriberInfo> getAllSubscribers() {
        return subscribers.values();
    }

    /**
     * Gets subscriber by client ID.
     */
    public SubscriberInfo getSubscriber(String clientId) {
        return subscribers.get(clientId);
    }
}
