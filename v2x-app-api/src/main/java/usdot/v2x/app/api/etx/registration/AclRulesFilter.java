package usdot.v2x.app.api.etx.registration;

import java.util.List;
import java.util.Map;

/**
 * Filters ACL rule responses to limit client subscribe permissions.
 */
public final class AclRulesFilter {

    static final String TIM_SUBSCRIBE_TOPIC = "vzimp/1/GeoRelevance/+/+/Public/j2735_gr/TIM/+";

    private static final List<String> LIMITED_SUBSCRIBE_TOPICS = List.of(TIM_SUBSCRIBE_TOPIC);

    private AclRulesFilter() {
    }

    /**
     * Limits subscribe topics in ACL rules to TIM GeoRelevance messages only.
     *
     * @param aclRules ACL rules response from Thingspace (expected to be a list of rule maps)
     * @return the same object with subscribe topics filtered
     */
    @SuppressWarnings("unchecked")
    public static Object filterSubscribeTopics(Object aclRules) {
        if (!(aclRules instanceof List<?> rules)) {
            return aclRules;
        }

        for (Object rule : rules) {
            if (rule instanceof Map<?, ?> ruleMap) {
                ((Map<String, Object>) ruleMap).put("subscribe", LIMITED_SUBSCRIBE_TOPICS);
            }
        }

        return aclRules;
    }
}
