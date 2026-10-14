package usdot.v2x.app.api.etx.registration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import usdot.v2x.app.api.config.etx.EtxProperties;

/**
 * Filters ACL rule responses to limit client subscribe permissions.
 * <p>
 * Subscribe topics use the Thingspace ACL syntax, where {@code *} is a wildcard
 * segment and {@code |} separates alternatives within a segment. Topics are
 * only ever narrowed, never widened.
 */
public final class AclRulesFilter {

    static final String PUBLIC_VENDOR = "Public";
    static final List<String> ALLOWED_FORMATS = List.of("j2735_gr", "j2735");

    private static final String WILDCARD = "*";
    private static final String ALTERNATIVE_SEPARATOR = "|";
    private static final String GEO_RELEVANCE = "GeoRelevance";
    private static final String PRIVATE = "Private";
    private static final String SMALL = "Small";

    // vzimp/1/GeoRelevance/{type}/{subtype}/{vendors}/{formats}/{msgTypes}/*
    private static final int NAMESPACE_INDEX = 2;
    private static final int SMALL_INDEX = 3;
    private static final int GEO_RELEVANCE_VENDORS_INDEX = 5;
    private static final int GEO_RELEVANCE_FORMATS_INDEX = 6;
    private static final int GEO_RELEVANCE_MESSAGE_TYPES_INDEX = 7;
    // vzimp/1/Private/*/*/*/{formats}/*/*
    private static final int PRIVATE_FORMATS_INDEX = 6;

    private AclRulesFilter() {
    }

    /**
     * Narrows existing subscribe permissions in ACL rules according to the
     * subscribe filter configuration. Rules without a non-empty subscribe
     * collection are left untouched, so this never grants subscribe rights a rule
     * did not already have.
     *
     * @param aclRules ACL rules response from Thingspace (expected to be a list of rule maps)
     * @param config   subscribe filter configuration
     * @return the same object with existing subscribe topics narrowed
     */
    @SuppressWarnings("unchecked")
    public static Object filterSubscribeTopics(Object aclRules, EtxProperties.SubscribeFilter config) {
        if (!(aclRules instanceof List<?> rules)) {
            return aclRules;
        }

        for (Object rule : rules) {
            if (rule instanceof Map<?, ?> ruleMap) {
                Map<String, Object> aclRule = (Map<String, Object>) ruleMap;
                if (aclRule.get("subscribe") instanceof Collection<?> subscribe && !subscribe.isEmpty()) {
                    aclRule.put("subscribe", narrowTopics(subscribe, config));
                }
            }
        }

        return aclRules;
    }

    private static List<String> narrowTopics(Collection<?> topics, EtxProperties.SubscribeFilter config) {
        Set<String> narrowed = new LinkedHashSet<>();
        for (Object topic : topics) {
            if (topic instanceof String topicString) {
                String narrowedTopic = narrowTopic(topicString, config);
                if (narrowedTopic != null) {
                    narrowed.add(narrowedTopic);
                }
            }
        }
        return new ArrayList<>(narrowed);
    }

    /**
     * @return the narrowed topic, or {@code null} if the topic should be dropped
     */
    private static String narrowTopic(String topic, EtxProperties.SubscribeFilter config) {
        String[] segments = topic.split("/", -1);
        if (segments.length <= NAMESPACE_INDEX) {
            return null;
        }

        return switch (segments[NAMESPACE_INDEX]) {
            case GEO_RELEVANCE -> narrowGeoRelevanceTopic(segments, config);
            case PRIVATE -> config.isPrivateEnabled() ? narrowPrivateTopic(segments) : null;
            default -> null;
        };
    }

    private static String narrowGeoRelevanceTopic(String[] segments, EtxProperties.SubscribeFilter config) {
        if (segments.length <= GEO_RELEVANCE_MESSAGE_TYPES_INDEX || SMALL.equals(segments[SMALL_INDEX])) {
            return null;
        }

        String vendors = segments[GEO_RELEVANCE_VENDORS_INDEX];
        if (!WILDCARD.equals(vendors) && !config.isGeoRelevanceIncludeVendors()) {
            vendors = intersect(vendors, List.of(PUBLIC_VENDOR));
        }
        String formats = intersect(segments[GEO_RELEVANCE_FORMATS_INDEX], ALLOWED_FORMATS);
        String messageTypes = intersect(segments[GEO_RELEVANCE_MESSAGE_TYPES_INDEX],
                config.getGeoRelevanceMessageTypes());

        if (vendors.isEmpty() || formats.isEmpty() || messageTypes.isEmpty()) {
            return null;
        }

        segments[GEO_RELEVANCE_VENDORS_INDEX] = vendors;
        segments[GEO_RELEVANCE_FORMATS_INDEX] = formats;
        segments[GEO_RELEVANCE_MESSAGE_TYPES_INDEX] = messageTypes;
        return String.join("/", segments);
    }

    private static String narrowPrivateTopic(String[] segments) {
        if (segments.length <= PRIVATE_FORMATS_INDEX) {
            return null;
        }

        String formats = intersect(segments[PRIVATE_FORMATS_INDEX], ALLOWED_FORMATS);
        if (formats.isEmpty()) {
            return null;
        }

        segments[PRIVATE_FORMATS_INDEX] = formats;
        return String.join("/", segments);
    }

    /**
     * Intersects a {@code |}-separated topic segment with the allowed values,
     * matching case-insensitively and keeping the segment's original casing. A
     * wildcard segment is replaced by all allowed values.
     *
     * @return the intersected segment, or an empty string if nothing matches
     */
    private static String intersect(String segment, Collection<String> allowed) {
        if (allowed == null) {
            return "";
        }

        Set<String> result = new LinkedHashSet<>();
        if (WILDCARD.equals(segment)) {
            for (String value : allowed) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return String.join(ALTERNATIVE_SEPARATOR, result);
        }

        Set<String> allowedNormalized = new LinkedHashSet<>();
        for (String value : allowed) {
            allowedNormalized.add(value.trim().toLowerCase(Locale.ROOT));
        }
        for (String value : segment.split("\\|")) {
            if (allowedNormalized.contains(value.toLowerCase(Locale.ROOT))) {
                result.add(value);
            }
        }
        return String.join(ALTERNATIVE_SEPARATOR, result);
    }
}
