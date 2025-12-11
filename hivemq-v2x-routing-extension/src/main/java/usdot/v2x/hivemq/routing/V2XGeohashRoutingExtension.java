package usdot.v2x.hivemq.routing;

import com.hivemq.extension.sdk.api.ExtensionMain;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartOutput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopOutput;
import com.hivemq.extension.sdk.api.services.Services;
import lombok.extern.slf4j.Slf4j;

/**
 * Main HiveMQ Extension for V2X Geohash-based Routing
 * 
 * This extension:
 * 1. Intercepts BSM messages published to /v2x/1/ingress/bsm
 * 2. Extracts location from BSM (ASN.1 or JSON)
 * 3. Routes to geohash topics: /v2x/1/geo/{char1}/{char2}/.../{char7}/bsm
 * 4. Rewrites egress subscriptions: /v2x/1/egress/{messageType}/# ->
 * /v2x/1/geo/+/+/+/+/+/+/+/{messageType}
 */
@Slf4j
public class V2XGeohashRoutingExtension implements ExtensionMain {

    private V2XPublishInterceptor publishInterceptor;
    private V2XSubscriptionInterceptor subscriptionInterceptor;

    @Override
    public void extensionStart(
            @NotNull ExtensionStartInput extensionStartInput,
            @NotNull ExtensionStartOutput extensionStartOutput) {

        log.info("Starting V2X Geohash Routing Extension v1.0.0");

        try {
            // Initialize interceptors (for ingress BSM routing and egress topic rewriting)
            publishInterceptor = new V2XPublishInterceptor();
            subscriptionInterceptor = new V2XSubscriptionInterceptor();

            // Register the interceptors using ClientInitializer pattern
            // According to HiveMQ documentation:
            // https://docs.hivemq.com/hivemq/latest/extensions/interceptors.html
            // Publish and Subscribe interceptors are registered via ClientContext, not
            // GlobalInterceptorRegistry
            Services.initializerRegistry().setClientInitializer((initializerInput, clientContext) -> {
                clientContext.addPublishInboundInterceptor(publishInterceptor);
                clientContext.addSubscribeInboundInterceptor(subscriptionInterceptor);
            });

            log.info("V2X Geohash Routing Extension started successfully");

        } catch (Exception e) {
            log.error("Failed to start V2X Geohash Routing Extension", e);
        }
    }

    @Override
    public void extensionStop(
            @NotNull ExtensionStopInput extensionStopInput,
            @NotNull ExtensionStopOutput extensionStopOutput) {

        log.info("Stopping V2X Geohash Routing Extension");

        try {
            if (publishInterceptor != null) {
                publishInterceptor.cleanup();
            }

            log.info("V2X Geohash Routing Extension stopped successfully");
        } catch (Exception e) {
            log.error("Error during extension stop", e);
        }
    }
}
