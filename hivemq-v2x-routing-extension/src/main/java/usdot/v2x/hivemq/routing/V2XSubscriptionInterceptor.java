package usdot.v2x.hivemq.routing;

import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.interceptor.subscribe.SubscribeInboundInterceptor;
import com.hivemq.extension.sdk.api.interceptor.subscribe.parameter.SubscribeInboundInput;
import com.hivemq.extension.sdk.api.interceptor.subscribe.parameter.SubscribeInboundOutput;
import com.hivemq.extension.sdk.api.packets.subscribe.ModifiableSubscription;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Intercepts inbound SUBSCRIBE packets to egress topics,
 * rewrites subscriptions to geohash-based topics.
 * 
 * Flow:
 * - CV MEC subscribes to: /v2x/1/egress/{messageType}/#
 * - Extension rewrites to: /v2x/1/geo/+/+/+/+/+/+/+/{messageType}
 * 
 * Note: In a production system, you would dynamically determine
 * which geohash topics to subscribe to based on the client's location.
 * For now, we subscribe to all geohash topics for the message type.
 */
@Slf4j
public class V2XSubscriptionInterceptor implements SubscribeInboundInterceptor {

    @Override
    public void onInboundSubscribe(
            @NotNull SubscribeInboundInput subscribeInboundInput,
            @NotNull SubscribeInboundOutput subscribeInboundOutput) {

        com.hivemq.extension.sdk.api.packets.subscribe.ModifiableSubscribePacket modifiablePacket = subscribeInboundOutput
                .getSubscribePacket();

        // Get the list of modifiable subscriptions
        List<ModifiableSubscription> subscriptions = modifiablePacket.getSubscriptions();

        // Iterate through subscriptions and modify egress topics
        for (ModifiableSubscription subscription : subscriptions) {
            String topicFilter = subscription.getTopicFilter();

            // Check if this is an egress topic subscription
            if (topicFilter.startsWith("v2x/1/egress/")) {
                // Extract message type from egress topic
                // Format: v2x/1/egress/{messageType}/# or v2x/1/egress/{messageType}
                String[] parts = topicFilter.split("/");
                if (parts.length >= 4) {
                    String messageType = parts[3].toLowerCase();

                    // Rewrite to geohash topic pattern
                    // Format: v2x/1/geo/+/+/+/+/+/+/+/{messageType}
                    String geohashTopicFilter = String.format("v2x/1/geo/+/+/+/+/+/+/+/%s", messageType);

                    log.debug("Rewriting subscription from {} to {}", topicFilter, geohashTopicFilter);

                    // Modify the topic filter directly on the modifiable subscription
                    subscription.setTopicFilter(geohashTopicFilter);
                }
            }
        }
    }
}
