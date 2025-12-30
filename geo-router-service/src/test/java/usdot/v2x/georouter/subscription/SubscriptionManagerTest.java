package usdot.v2x.georouter.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import usdot.v2x.georouter.config.GeoRoutingConfig;
import usdot.v2x.georouter.geospatial.GeohashRouter;
import usdot.v2x.georouter.models.SubscriberInfo;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SubscriptionManager.
 */
class SubscriptionManagerTest {

    private SubscriptionManager subscriptionManager;
    private GeohashRouter geohashRouter;

    @BeforeEach
    void setUp() {
        GeoRoutingConfig config = new GeoRoutingConfig();
        GeoRoutingConfig.Geohash geohashConfig = new GeoRoutingConfig.Geohash();
        geohashConfig.setPrecision(7);
        config.setGeohash(geohashConfig);

        geohashRouter = new GeohashRouter(config);
        subscriptionManager = new SubscriptionManager(geohashRouter);
    }

    @Test
    void testRegisterSubscriber() {
        SubscriberInfo subscriber = SubscriberInfo.builder()
                .clientId("test-client-1")
                .geohashes(Set.of("9q8yyk7", "9q8yyk8"))
                .messageTypes(Set.of("BSM"))
                .topicPattern("v2x/georelevance/{geohash}/BSM/+")
                .build();

        subscriptionManager.registerSubscriber(subscriber);

        SubscriberInfo retrieved = subscriptionManager.getSubscriber("test-client-1");
        assertNotNull(retrieved);
        assertEquals("test-client-1", retrieved.getClientId());
        assertEquals(2, retrieved.getGeohashes().size());
    }

    @Test
    void testUnregisterSubscriber() {
        SubscriberInfo subscriber = SubscriberInfo.builder()
                .clientId("test-client-2")
                .geohashes(Set.of("9q8yyk7"))
                .messageTypes(Set.of("BSM"))
                .build();

        subscriptionManager.registerSubscriber(subscriber);
        assertNotNull(subscriptionManager.getSubscriber("test-client-2"));

        subscriptionManager.unregisterSubscriber("test-client-2");
        assertNull(subscriptionManager.getSubscriber("test-client-2"));
    }

    @Test
    void testFindMatchingSubscribers() {
        // Register subscribers
        SubscriberInfo subscriber1 = SubscriberInfo.builder()
                .clientId("client-1")
                .geohashes(Set.of("9q8yyk7"))
                .messageTypes(Set.of("BSM"))
                .build();

        SubscriberInfo subscriber2 = SubscriberInfo.builder()
                .clientId("client-2")
                .geohashes(Set.of("9q8yyk8"))
                .messageTypes(Set.of("TIM"))
                .build();

        subscriptionManager.registerSubscriber(subscriber1);
        subscriptionManager.registerSubscriber(subscriber2);

        // Find matches for BSM in geohash 9q8yyk7
        Set<String> relevantGeohashes = Set.of("9q8yyk7");
        Set<SubscriberInfo> matches = subscriptionManager.findMatchingSubscribers(
                relevantGeohashes, "BSM");

        assertEquals(1, matches.size());
        assertTrue(matches.stream().anyMatch(s -> s.getClientId().equals("client-1")));
    }

    @Test
    void testUpdateSubscriberGeohashes() {
        SubscriberInfo subscriber = SubscriberInfo.builder()
                .clientId("client-3")
                .geohashes(Set.of("9q8yyk7"))
                .messageTypes(Set.of("BSM"))
                .build();

        subscriptionManager.registerSubscriber(subscriber);

        // Update geohashes
        Set<String> newGeohashes = Set.of("9q8yyk8", "9q8yyk9");
        subscriptionManager.updateSubscriberGeohashes("client-3", newGeohashes);

        SubscriberInfo updated = subscriptionManager.getSubscriber("client-3");
        assertEquals(2, updated.getGeohashes().size());
        assertTrue(updated.getGeohashes().contains("9q8yyk8"));
        assertTrue(updated.getGeohashes().contains("9q8yyk9"));
    }
}
