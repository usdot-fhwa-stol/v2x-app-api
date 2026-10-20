package usdot.v2x.app.api.etx.registration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import usdot.v2x.app.api.config.etx.EtxProperties;

class AclRulesFilterTest {

    private static final List<String> PUBLISH_TOPICS = List.of(
            "vzimp/1/Private/*/${clientType}/${clientSubtype}/LeidosDevice/j2735_gr|j2735|JSON/*",
            "vzimp/1/GeoRelevance/${clientType}/${clientSubtype}/Public|LeidosDevice/j2735_gr/BSM|PSM|SPAT|MAP|RSA|TIM|SDSM|TUM|SRM|SSM");

    private static final List<String> SUBSCRIBE_TOPICS = List.of(
            "vzimp/1/GeoRelevance/*/*/VzTrafficDensity/JSON/*/*",
            "vzimp/1/GeoRelevance/Small/*/*/Public|LeidosPlatform|LeidosDevice|LeidosOpenSource/j2735_gr|j2735/BSM|PSM|RSA|TIM|EVA|SDSM|TAM|SRM|SSM/*",
            "vzimp/1/Private/*/*/*/j2735|j2735_gr|JSON/*/*",
            "vzimp/1/GeoRelevance/Small/*/*/VzTrafficDensity/JSON/*/*",
            "vzimp/1/GeoRelevance/Small/*/*/*/j2735_gr|j2735/SPAT|MAP/*",
            "vzimp/1/GeoRelevance/*/*/Public|LeidosPlatform|LeidosDevice|LeidosOpenSource/j2735_gr|j2735/BSM|PSM|RSA|TIM|EVA|SDSM|TAM|SRM|SSM/*",
            "vzimp/1/GeoRelevance/*/*/*/j2735_gr|j2735/SPAT|MAP/*");

    private static final String PUBLIC_BSM_TIM_TOPIC = "vzimp/1/GeoRelevance/*/*/Public/j2735_gr|j2735/BSM|TIM/*";
    private static final String WILDCARD_SPAT_MAP_TOPIC = "vzimp/1/GeoRelevance/*/*/*/j2735_gr|j2735/SPAT|MAP/*";
    private static final String PRIVATE_TOPIC = "vzimp/1/Private/*/*/*/j2735|j2735_gr/*/*";

    private static Map<String, Object> sampleRule() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("name", "ts.device.mqtt.imp:${clientType}.${clientSubtype}.LeidosDevice");
        rule.put("subscribeLimit", 10);
        rule.put("publishRateLimit", 15);
        rule.put("publish", PUBLISH_TOPICS);
        rule.put("subscribe", SUBSCRIBE_TOPICS);
        return rule;
    }

    private static Object filterSubscribe(EtxProperties.SubscribeFilter config) {
        Map<String, Object> rule = sampleRule();
        AclRulesFilter.filterSubscribeTopics(List.of(rule), config);
        return rule.get("subscribe");
    }

    @Test
    void subscribeFilterDefaults() {
        EtxProperties.SubscribeFilter config = new EtxProperties().getAclRules().getSubscribeFilter();

        assertFalse(config.isEnabled());
        assertEquals(List.of("BSM", "SPAT", "MAP", "TIM"), config.getGeoRelevanceMessageTypes());
        assertFalse(config.isGeoRelevanceIncludeVendors());
        assertFalse(config.isPrivateEnabled());
    }

    @Test
    void filterSubscribeTopics_defaultsKeepPublicAndWildcardVendorTopics() {
        Map<String, Object> rule = sampleRule();
        List<Map<String, Object>> aclRules = List.of(rule);

        Object filtered = AclRulesFilter.filterSubscribeTopics(aclRules, new EtxProperties.SubscribeFilter());

        assertSame(aclRules, filtered);
        assertEquals(List.of(PUBLIC_BSM_TIM_TOPIC, WILDCARD_SPAT_MAP_TOPIC), rule.get("subscribe"));
        assertEquals(PUBLISH_TOPICS, rule.get("publish"));
    }

    @Test
    void filterSubscribeTopics_includeVendorsKeepsAllListedVendors() {
        EtxProperties.SubscribeFilter config = new EtxProperties.SubscribeFilter();
        config.setGeoRelevanceIncludeVendors(true);

        assertEquals(List.of(
                "vzimp/1/GeoRelevance/*/*/Public|LeidosPlatform|LeidosDevice|LeidosOpenSource/j2735_gr|j2735/BSM|TIM/*",
                WILDCARD_SPAT_MAP_TOPIC), filterSubscribe(config));
    }

    @Test
    void filterSubscribeTopics_privateEnabledKeepsPrivateTopicWithoutJson() {
        EtxProperties.SubscribeFilter config = new EtxProperties.SubscribeFilter();
        config.setPrivateEnabled(true);

        assertEquals(List.of(PRIVATE_TOPIC, PUBLIC_BSM_TIM_TOPIC, WILDCARD_SPAT_MAP_TOPIC), filterSubscribe(config));
    }

    @Test
    void filterSubscribeTopics_dropsWildcardVendorTopicWhenSpatAndMapNotConfigured() {
        EtxProperties.SubscribeFilter config = new EtxProperties.SubscribeFilter();
        config.setGeoRelevanceMessageTypes(List.of("BSM", "TIM"));

        assertEquals(List.of(PUBLIC_BSM_TIM_TOPIC), filterSubscribe(config));
    }

    @Test
    void filterSubscribeTopics_timOnly() {
        EtxProperties.SubscribeFilter config = new EtxProperties.SubscribeFilter();
        config.setGeoRelevanceMessageTypes(List.of("TIM"));

        assertEquals(List.of("vzimp/1/GeoRelevance/*/*/Public/j2735_gr|j2735/TIM/*"), filterSubscribe(config));
    }

    @Test
    void filterSubscribeTopics_matchesMessageTypesCaseInsensitively() {
        EtxProperties.SubscribeFilter config = new EtxProperties.SubscribeFilter();
        config.setGeoRelevanceMessageTypes(List.of(" tim ", "spat"));

        assertEquals(List.of(
                "vzimp/1/GeoRelevance/*/*/Public/j2735_gr|j2735/TIM/*",
                "vzimp/1/GeoRelevance/*/*/*/j2735_gr|j2735/SPAT/*"), filterSubscribe(config));
    }

    @Test
    void filterSubscribeTopics_expandsWildcardFormatsAndMessageTypesToConfiguredValues() {
        Map<String, Object> rule = new HashMap<>();
        rule.put("subscribe", List.of("vzimp/1/GeoRelevance/*/*/Public/*/*/*"));

        AclRulesFilter.filterSubscribeTopics(List.of(rule), new EtxProperties.SubscribeFilter());

        assertEquals(List.of("vzimp/1/GeoRelevance/*/*/Public/j2735_gr|j2735/BSM|SPAT|MAP|TIM/*"),
                rule.get("subscribe"));
    }

    @Test
    void filterSubscribeTopics_returnsEmptyListWhenNothingMatches() {
        Map<String, Object> rule = new HashMap<>();
        rule.put("subscribe", List.of("vzimp/1/GeoRelevance/*/*/VzTrafficDensity/JSON/*/*", "unknown/topic"));

        AclRulesFilter.filterSubscribeTopics(List.of(rule), new EtxProperties.SubscribeFilter());

        assertEquals(List.of(), rule.get("subscribe"));
    }

    @Test
    void filterSubscribeTopics_returnsUnchangedWhenNotList() {
        Object aclRules = "not-a-list";

        assertSame(aclRules, AclRulesFilter.filterSubscribeTopics(aclRules, new EtxProperties.SubscribeFilter()));
    }

    @Test
    void filterSubscribeTopics_skipsNonMapEntries() {
        List<Object> aclRules = new ArrayList<>();
        aclRules.add("invalid-rule");

        assertSame(aclRules, AclRulesFilter.filterSubscribeTopics(aclRules, new EtxProperties.SubscribeFilter()));
    }

    @Test
    void filterSubscribeTopics_doesNotGrantSubscribeWhenRuleHasNoSubscribeKey() {
        Map<String, Object> rule = new HashMap<>();
        rule.put("publish", PUBLISH_TOPICS);
        List<Map<String, Object>> aclRules = List.of(rule);

        AclRulesFilter.filterSubscribeTopics(aclRules, new EtxProperties.SubscribeFilter());

        assertFalse(rule.containsKey("subscribe"));
    }

    @Test
    void filterSubscribeTopics_keepsEmptySubscribeListEmpty() {
        List<String> subscribe = new ArrayList<>();
        Map<String, Object> rule = new HashMap<>();
        rule.put("subscribe", subscribe);
        List<Map<String, Object>> aclRules = List.of(rule);

        AclRulesFilter.filterSubscribeTopics(aclRules, new EtxProperties.SubscribeFilter());

        assertSame(subscribe, rule.get("subscribe"));
        assertEquals(0, subscribe.size());
    }

    @Test
    void filterSubscribeTopics_keepsNullSubscribeValue() {
        Map<String, Object> rule = new HashMap<>();
        rule.put("subscribe", null);
        List<Map<String, Object>> aclRules = List.of(rule);

        AclRulesFilter.filterSubscribeTopics(aclRules, new EtxProperties.SubscribeFilter());

        assertTrue(rule.containsKey("subscribe"));
        assertNull(rule.get("subscribe"));
    }
}
