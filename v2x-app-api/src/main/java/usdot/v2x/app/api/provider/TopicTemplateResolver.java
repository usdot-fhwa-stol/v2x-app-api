package usdot.v2x.app.api.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import usdot.v2x.app.api.models.dto.provider.GeohashTopicSpec;

/**
 * Replaces whole-level topic variables such as {@code {agency}} in provider ACL
 * topic templates with their configured values, server-side, before rules are
 * returned to the mobile app. The geohash token ({@code @}) and wildcards are
 * left for the app to expand.
 * <p>
 * Example: {@code MAP/unsigned/J2735/{agency}/@} with {@code agency=DDOT}
 * resolves to {@code MAP/unsigned/J2735/DDOT/@}.
 */
public final class TopicTemplateResolver {

    private static final String LEVEL_SEPARATOR = "/";

    private TopicTemplateResolver() {
    }

    /**
     * Resolves every template in order.
     *
     * @throws UnresolvedTopicVariableException if a template uses a variable
     *                                          that has no value
     * @throws IllegalArgumentException         if a value is not a valid literal
     *                                          topic level
     */
    public static List<String> resolve(Collection<String> templates, Map<String, String> variables,
            GeohashTopicSpec spec) {
        List<String> resolved = new ArrayList<>(templates.size());
        for (String template : templates) {
            resolved.add(resolve(template, variables, spec));
        }
        return resolved;
    }

    /**
     * Resolves one template.
     *
     * @see #resolve(Collection, Map, GeohashTopicSpec)
     */
    public static String resolve(String template, Map<String, String> variables, GeohashTopicSpec spec) {
        if (template == null || !template.contains("{")) {
            return template;
        }
        String[] levels = template.split(LEVEL_SEPARATOR, -1);
        for (int i = 0; i < levels.length; i++) {
            if (TopicTemplateValidator.isPlaceholder(levels[i])) {
                String name = TopicTemplateValidator.placeholderName(levels[i]);
                String value = variables == null ? null : variables.get(name);
                if (value == null) {
                    throw new UnresolvedTopicVariableException(name, template);
                }
                TopicTemplateValidator.validateVariableValue(name, value, spec);
                levels[i] = value;
            }
        }
        return String.join(LEVEL_SEPARATOR, levels);
    }

    /**
     * @return the distinct variable names used by the templates, in order of
     *         first use
     */
    public static Set<String> placeholderNames(Collection<String> templates) {
        Set<String> names = new LinkedHashSet<>();
        for (String template : templates) {
            if (template == null) {
                continue;
            }
            for (String level : template.split(LEVEL_SEPARATOR, -1)) {
                if (TopicTemplateValidator.isPlaceholder(level)) {
                    names.add(TopicTemplateValidator.placeholderName(level));
                }
            }
        }
        return names;
    }

    /**
     * Thrown when a topic template references a variable with no configured
     * value. This is a server configuration problem, not a client error.
     */
    public static class UnresolvedTopicVariableException extends IllegalStateException {

        private final String variable;

        public UnresolvedTopicVariableException(String variable, String template) {
            super("Topic variable '{" + variable + "}' used in '" + template + "' has no configured value");
            this.variable = variable;
        }

        public String getVariable() {
            return variable;
        }
    }
}
