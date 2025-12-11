package usdot.v2x.hivemq.routing;

import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.interceptor.publish.PublishInboundInterceptor;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundInput;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundOutput;
import com.hivemq.extension.sdk.api.packets.publish.PublishPacket;
import lombok.extern.slf4j.Slf4j;
import usdot.v2x.hivemq.routing.service.GeohashRoutingService;
import usdot.v2x.hivemq.routing.service.LocationData;
import usdot.v2x.hivemq.routing.service.MessageDecoderService;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Intercepts inbound PUBLISH packets to ingress topics,
 * extracts location from BSM messages, and routes to geohash topics.
 * 
 * Flow:
 * - CV MEC publishes BSM to: /v2x/1/ingress/bsm
 * - Extension extracts location from BSM
 * - Extension routes to:
 * /v2x/1/geo/{char1}/{char2}/{char3}/{char4}/{char5}/{char6}/{char7}/bsm
 */
@Slf4j
public class V2XPublishInterceptor implements PublishInboundInterceptor {

    private final MessageDecoderService decoderService;
    private final GeohashRoutingService routingService;

    public V2XPublishInterceptor() {
        this.decoderService = new MessageDecoderService();
        this.routingService = new GeohashRoutingService();
    }

    @Override
    public void onInboundPublish(
            @NotNull PublishInboundInput publishInboundInput,
            @NotNull PublishInboundOutput publishInboundOutput) {

        final PublishPacket publishPacket = publishInboundInput.getPublishPacket();
        final String topic = publishPacket.getTopic();

        // Only process ingress BSM topics
        if (!topic.equals("v2x/1/ingress/bsm")) {
            return;
        }

        log.debug("Intercepted publish to ingress topic: {}", topic);

        // Process asynchronously to avoid blocking
        CompletableFuture.runAsync(() -> {
            try {
                // Get payload (returns Optional)
                final ByteBuffer payload = publishPacket.getPayload().orElse(null);
                if (payload == null || !payload.hasRemaining()) {
                    log.debug("Empty payload for topic: {}", topic);
                    return;
                }

                // Decode BSM and extract location
                LocationData location = decoderService.decodeAndExtractBSMLocation(payload);

                if (location == null) {
                    log.debug("Could not extract location from BSM on topic: {}", topic);
                    return;
                }

                log.debug("Extracted location from BSM: lat={}, lon={}",
                        location.getLatitude(), location.getLongitude());

                // Route to geohash-based topics
                routingService.routeToGeohashTopics(
                        location,
                        "bsm",
                        payload);

            } catch (Exception e) {
                log.error("Error processing publish for topic {}: {}", topic, e.getMessage(), e);
            }
        });
    }

    public void cleanup() {
        if (decoderService != null) {
            decoderService.cleanup();
        }
        if (routingService != null) {
            routingService.cleanup();
        }
    }
}
