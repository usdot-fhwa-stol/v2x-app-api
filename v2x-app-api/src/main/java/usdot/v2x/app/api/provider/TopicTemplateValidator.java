package usdot.v2x.app.api.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import usdot.v2x.app.api.models.dto.provider.GeohashTopicSpec;

/**
 * Validates provider ACL topic templates.
 * <p>
 * Templates use ETX ACL syntax plus a geohash token:
 * <ul>
 * <li>{@code @} (configurable) - a whole level that expands to the
 * {@code /}-delimited geohash path</li>
 * <li>{@code |} - alternatives within one level, expanded by the app into one
 * topic per alternative</li>
 * <li>{@code +} / {@code *} - single-level wildcards ({@code *} is the ETX form)</li>
 * <li>{@code #} - multi-level wildcard, last level only</li>
 * </ul>
 * Publish templates may not contain wildcards. All methods throw
 * {@link IllegalArgumentException}, which the global exception handler maps to
 * HTTP 400.
 */
public final class TopicTemplateValidator {

    static final int MAX_LEVELS = 12;
    static final int MAX_GRID_RADIUS = 2;

    private static final String LEVEL_SEPARATOR = "/";
    private static final String ALTERNATIVE_SEPARATOR = "|";
    private static final String SINGLE_LEVEL_WILDCARD = "+";
    private static final String ETX_SINGLE_LEVEL_WILDCARD = "*";
    private static final String MULTI_LEVEL_WILDCARD = "#";
    private static final Set<String> WILDCARDS = Set.of(SINGLE_LEVEL_WILDCARD, ETX_SINGLE_LEVEL_WILDCARD,
            MULTI_LEVEL_WILDCARD);
    private static final Set<String> RESERVED_SYMBOLS = Set.of(LEVEL_SEPARATOR, ALTERNATIVE_SEPARATOR,
            SINGLE_LEVEL_WILDCARD, ETX_SINGLE_LEVEL_WILDCARD, MULTI_LEVEL_WILDCARD);

    private TopicTemplateValidator() {
    }

    /**
     * Validates a complete ACL rule.
     *
     * @param publish        publish topic templates (may be empty)
     * @param subscribe      subscribe topic templates (may be empty)
     * @param spec           geohash expansion rules
     * @param subscribeLimit maximum number of MQTT subscriptions the app may open,
     *                       or {@code null} for no limit
     */
    public static void validateRule(Collection<String> publish, Collection<String> subscribe,
            GeohashTopicSpec spec, Integer subscribeLimit) {
        validateSpec(spec);
        List<String> errors = new ArrayList<>();
        if (publish != null) {
            for (String topic : publish) {
                collectErrors(topic, spec, true, errors);
            }
        }
        if (subscribe != null) {
            for (String topic : subscribe) {
                collectErrors(topic, spec, false, errors);
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        if (subscribeLimit != null && subscribe != null) {
            int required = expandedSubscriptionCount(subscribe, spec);
            if (subscribeLimit < required) {
                throw new IllegalArgumentException("subscribeLimit " + subscribeLimit
                        + " is lower than the " + required + " subscriptions the subscribe topics expand to");
            }
        }
    }

    /**
     * Validates the geohash expansion rules.
     */
    public static void validateSpec(GeohashTopicSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("geohash spec is required");
        }
        String symbol = spec.getSymbol();
        if (symbol == null || symbol.isBlank() || RESERVED_SYMBOLS.stream().anyMatch(symbol::contains)) {
            throw new IllegalArgumentException("geohash symbol must be non-blank and must not contain / | + * #");
        }
        Integer levels = spec.getLevels();
        if (levels == null || levels < 1 || levels > MAX_LEVELS) {
            throw new IllegalArgumentException("geohash levels must be between 1 and " + MAX_LEVELS);
        }
        requirePrecision("publishPrecision", spec.getPublishPrecision(), levels);
        requirePrecision("subscribePrecision", spec.getSubscribePrecision(), levels);
        Integer radius = spec.getSubscribeGridRadius();
        if (radius == null || radius < 0 || radius > MAX_GRID_RADIUS) {
            throw new IllegalArgumentException("subscribeGridRadius must be between 0 and " + MAX_GRID_RADIUS);
        }
    }

    /**
     * Validates one publish topic template.
     */
    public static void validatePublishTopic(String topic, GeohashTopicSpec spec) {
        validateTopic(topic, spec, true);
    }

    /**
     * Validates one subscribe topic template.
     */
    public static void validateSubscribeTopic(String topic, GeohashTopicSpec spec) {
        validateTopic(topic, spec, false);
    }

    /**
     * Counts the MQTT subscriptions the app opens for the given subscribe
     * templates: each template contributes the product of its {@code |}
     * alternatives, multiplied by the grid cell count when it contains the
     * geohash token.
     */
    public static int expandedSubscriptionCount(Collection<String> subscribe, GeohashTopicSpec spec) {
        int total = 0;
        for (String topic : subscribe) {
            int count = 1;
            boolean hasGeohash = false;
            for (String level : topic.split(LEVEL_SEPARATOR, -1)) {
                if (level.equals(spec.getSymbol())) {
                    hasGeohash = true;
                } else {
                    count *= level.split("\\" + ALTERNATIVE_SEPARATOR, -1).length;
                }
            }
            total += hasGeohash ? count * spec.subscribeCellCount() : count;
        }
        return total;
    }

    private static void validateTopic(String topic, GeohashTopicSpec spec, boolean publish) {
        validateSpec(spec);
        List<String> errors = new ArrayList<>();
        collectErrors(topic, spec, publish, errors);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
    }

    private static void collectErrors(String topic, GeohashTopicSpec spec, boolean publish, List<String> errors) {
        String kind = publish ? "publish" : "subscribe";
        if (topic == null || topic.isBlank()) {
            errors.add(kind + " topic must not be blank");
            return;
        }
        String prefix = kind + " topic '" + topic + "': ";
        if (!topic.equals(topic.strip())) {
            errors.add(prefix + "must not have leading or trailing whitespace");
        }

        String symbol = spec.getSymbol();
        String[] levels = topic.split(LEVEL_SEPARATOR, -1);
        int geohashLevels = 0;
        for (int i = 0; i < levels.length; i++) {
            String level = levels[i];
            boolean last = i == levels.length - 1;

            if (level.isEmpty()) {
                errors.add(prefix + "must not contain empty levels or leading/trailing '/'");
                continue;
            }
            if (level.equals(symbol)) {
                geohashLevels++;
                continue;
            }
            if (level.contains(symbol)) {
                errors.add(prefix + "'" + symbol + "' must occupy a whole level");
                continue;
            }
            if (WILDCARDS.contains(level)) {
                if (publish) {
                    errors.add(prefix + "publish topics must not contain wildcards");
                } else if (MULTI_LEVEL_WILDCARD.equals(level) && !last) {
                    errors.add(prefix + "'#' is only allowed as the last level");
                }
                continue;
            }
            for (String alternative : level.split("\\" + ALTERNATIVE_SEPARATOR, -1)) {
                if (alternative.isEmpty()) {
                    errors.add(prefix + "'|' alternatives must not be empty");
                } else if (WILDCARDS.stream().anyMatch(alternative::contains)) {
                    errors.add(prefix + "wildcards must occupy a whole level");
                }
            }
        }
        if (geohashLevels > 1) {
            errors.add(prefix + "'" + symbol + "' may appear at most once");
        }
    }

    private static void requirePrecision(String name, Integer precision, int levels) {
        if (precision == null || precision < 1 || precision > levels) {
            throw new IllegalArgumentException(name + " must be between 1 and levels (" + levels + ")");
        }
    }
}
