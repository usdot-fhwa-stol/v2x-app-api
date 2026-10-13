package usdot.v2x.app.api.etx.registration;

import java.util.Collection;
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
     * Narrows existing subscribe permissions in ACL rules to TIM GeoRelevance topics.
     * Rules without a non-empty subscribe collection are left untouched, so this
     * never grants subscribe rights a rule did not already have.
     *
     * @param aclRules ACL rules response from Thingspace (expected to be a list of rule maps)
     * @return the same object with existing subscribe topics narrowed
     */
    @SuppressWarnings("unchecked")
    public static Object filterSubscribeTopics(Object aclRules) {
        if (!(aclRules instanceof List<?> rules)) {
            return aclRules;
        }

        for (Object rule : rules) {
            if (rule instanceof Map<?, ?> ruleMap) {
                Map<String, Object> aclRule = (Map<String, Object>) ruleMap;
                if (aclRule.get("subscribe") instanceof Collection<?> subscribe && !subscribe.isEmpty()) {
                    aclRule.put("subscribe", LIMITED_SUBSCRIBE_TOPICS);
                }
            }
        }

        return aclRules;
    }
}
