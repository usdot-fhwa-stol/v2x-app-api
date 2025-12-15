package usdot.v2x.app.api.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Utility class for building MQTT topic structures based on geohash precision levels.
 * 
 * Topic structure: /v2x/geohash/{geohash_precision_level_1}.../{geohash_precision_level_7}/BSM|SPAT|MAP|etc.
 */
@Component
@Slf4j
public class MqttTopicBuilder {
    
    private static final String TOPIC_PREFIX = "/v2x/geohash";
    
    /**
     * Builds a topic path from a geohash string.
     * Splits the geohash into individual characters and creates topic levels.
     * 
     * @param geohash The geohash string (up to 7 characters)
     * @param messageType The message type (BSM, SPAT, MAP, TIM, etc.)
     * @return The complete topic path
     */
    public String buildTopicFromGeohash(String geohash, String messageType) {
        if (geohash == null || geohash.isEmpty()) {
            throw new IllegalArgumentException("Geohash cannot be null or empty");
        }
        
        if (messageType == null || messageType.isEmpty()) {
            throw new IllegalArgumentException("Message type cannot be null or empty");
        }
        
        // Limit to 7 precision levels as per requirement
        String limitedGeohash = geohash.length() > 7 ? geohash.substring(0, 7) : geohash;
        
        StringBuilder topic = new StringBuilder(TOPIC_PREFIX);
        
        // Add each character of the geohash as a separate topic level
        for (char c : limitedGeohash.toCharArray()) {
            topic.append("/").append(c);
        }
        
        // Add message type at the end
        topic.append("/").append(messageType.toUpperCase());
        
        return topic.toString();
    }
    
    /**
     * Builds a topic pattern with wildcards for subscribing to multiple geohash levels.
     * 
     * @param geohashPrefix The geohash prefix (e.g., "9q8" for first 3 levels)
     * @param messageType The message type (BSM, SPAT, MAP, TIM, etc.)
     * @param useMultiLevelWildcard If true, uses # for multi-level wildcard, otherwise uses + for single level
     * @return The topic pattern with wildcards
     */
    public String buildTopicPattern(String geohashPrefix, String messageType, boolean useMultiLevelWildcard) {
        if (geohashPrefix == null || geohashPrefix.isEmpty()) {
            throw new IllegalArgumentException("Geohash prefix cannot be null or empty");
        }
        
        if (messageType == null || messageType.isEmpty()) {
            throw new IllegalArgumentException("Message type cannot be null or empty");
        }
        
        StringBuilder topic = new StringBuilder(TOPIC_PREFIX);
        
        // Add each character of the geohash prefix as a separate topic level
        for (char c : geohashPrefix.toCharArray()) {
            topic.append("/").append(c);
        }
        
        // Add wildcards for remaining levels
        int remainingLevels = 7 - geohashPrefix.length();
        if (remainingLevels > 0) {
            if (useMultiLevelWildcard) {
                // Use # for multi-level wildcard (matches all remaining levels)
                topic.append("/#");
            } else {
                // Use + for each remaining single level
                for (int i = 0; i < remainingLevels; i++) {
                    topic.append("/+");
                }
            }
        }
        
        // Add message type
        if (useMultiLevelWildcard) {
            // With #, message type is included in the wildcard
            // If we want to specify message type, we need to add it before #
            topic.insert(topic.length() - 2, "/" + messageType.toUpperCase());
        } else {
            topic.append("/").append(messageType.toUpperCase());
        }
        
        return topic.toString();
    }
    
    /**
     * Builds a topic pattern for all message types in a geohash area.
     * 
     * @param geohashPrefix The geohash prefix
     * @return The topic pattern with wildcard for message type
     */
    public String buildTopicPatternForAllMessageTypes(String geohashPrefix) {
        return buildTopicPattern(geohashPrefix, "+", false);
    }
    
    /**
     * Extracts geohash from a topic path.
     * 
     * @param topic The topic path
     * @return The geohash string, or null if the topic doesn't match the expected format
     */
    public String extractGeohashFromTopic(String topic) {
        if (topic == null || !topic.startsWith(TOPIC_PREFIX)) {
            return null;
        }
        
        String[] parts = topic.split("/");
        if (parts.length < 4) { // /v2x/geohash/{levels}...
            return null;
        }
        
        // Extract geohash characters (parts 3 to 9, up to 7 characters)
        StringBuilder geohash = new StringBuilder();
        for (int i = 3; i < parts.length && i < 10; i++) {
            String part = parts[i];
            // Stop if we hit a message type (uppercase) or wildcard
            if (part.matches("^[A-Z]+$") || part.equals("+") || part.equals("#")) {
                break;
            }
            geohash.append(part);
        }
        
        return geohash.length() > 0 ? geohash.toString() : null;
    }
    
    /**
     * Extracts message type from a topic path.
     * 
     * @param topic The topic path
     * @return The message type, or null if not found
     */
    public String extractMessageTypeFromTopic(String topic) {
        if (topic == null || !topic.startsWith(TOPIC_PREFIX)) {
            return null;
        }
        
        String[] parts = topic.split("/");
        // Message type should be the last part (or second to last if there's a trailing slash)
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i];
            if (part != null && !part.isEmpty() && part.matches("^[A-Z]+$")) {
                return part;
            }
        }
        
        return null;
    }
    
    /**
     * Validates if a topic pattern matches the expected structure.
     * 
     * @param topicPattern The topic pattern to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidTopicPattern(String topicPattern) {
        if (topicPattern == null || !topicPattern.startsWith(TOPIC_PREFIX)) {
            return false;
        }
        
        String[] parts = topicPattern.split("/");
        // Should have at least: /v2x/geohash/{at least one level}
        return parts.length >= 4;
    }
}

