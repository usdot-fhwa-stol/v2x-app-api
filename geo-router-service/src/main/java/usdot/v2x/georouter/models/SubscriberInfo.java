package usdot.v2x.georouter.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Information about a subscriber's geographic interests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriberInfo {
    /**
     * MQTT client ID of the subscriber.
     */
    private String clientId;

    /**
     * Set of geohashes this subscriber is interested in.
     */
    private Set<String> geohashes;

    /**
     * Set of message types this subscriber wants (e.g., "BSM", "TIM", "MAP").
     * Empty set means all message types.
     */
    private Set<String> messageTypes;

    /**
     * MQTT topic pattern this subscriber is subscribed to.
     */
    private String topicPattern;
}


