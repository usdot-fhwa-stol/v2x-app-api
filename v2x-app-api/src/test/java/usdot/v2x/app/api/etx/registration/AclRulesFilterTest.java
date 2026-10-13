package usdot.v2x.app.api.etx.registration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import usdot.v2x.app.api.config.etx.EtxProperties;

class AclRulesFilterTest {

    @Test
    void timSubscribeFilterIsOffByDefault() {
        assertFalse(new EtxProperties().getAclRules().isTimSubscribeFilterEnabled());
    }

    @Test
    void filterSubscribeTopics_limitsSubscribeTopicsToTimOnly() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("name", "ts.device.mqtt.imp:${clientType}.${clientSubtype}.MergentDevice");
        rule.put("subscribeLimit", 10);
        rule.put("publishRateLimit", 15);
        rule.put("publish", List.of(
                "vzimp/1/GeoRelevance/${clientType}/${clientSubtype}/Public|MergentDevice/j2735_gr/BSM|PSM|SPM|RSA|TIM",
                "vzimp/1/Private/*/${clientType}/${clientSubtype}/MergentDevice/j2735_gr|JSON/*"));
        rule.put("subscribe", List.of(
                "vzimp/1/Private/*/*/*/j2735_gr|JSON/*/*",
                "vzimp/1/GeoRelevance/Small/*/*/Public|VzTrafficDensity|VzMapManager|MergentPlatform|MergentDevice/j2735_gr|j2735|JSON/SPAT|MAP|EVA|RSA|TIM|SDSM/*",
                "vzimp/1/GeoRelevance/*/*/Public|VzTrafficDensity|VzMapManager|MergentPlatform|MergentDevice/j2735_gr|j2735|JSON/SPAT|MAP|EVA|RSA|TIM|SDSM/*"));
        List<Map<String, Object>> aclRules = List.of(rule);

        Object filtered = AclRulesFilter.filterSubscribeTopics(aclRules);

        assertSame(aclRules, filtered);
        assertEquals(List.of(AclRulesFilter.TIM_SUBSCRIBE_TOPIC), rule.get("subscribe"));
        assertEquals(2, ((List<?>) rule.get("publish")).size());
    }

    @Test
    void filterSubscribeTopics_returnsUnchangedWhenNotList() {
        Object aclRules = "not-a-list";

        assertSame(aclRules, AclRulesFilter.filterSubscribeTopics(aclRules));
    }

    @Test
    void filterSubscribeTopics_skipsNonMapEntries() {
        List<Object> aclRules = new ArrayList<>();
        aclRules.add("invalid-rule");

        assertSame(aclRules, AclRulesFilter.filterSubscribeTopics(aclRules));
    }
}
