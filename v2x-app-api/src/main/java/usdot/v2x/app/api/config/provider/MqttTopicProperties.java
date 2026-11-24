package usdot.v2x.app.api.config.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Default values for whole-level topic variables (for example
 * {@code {subscribeAgency}}) used in provider ACL topic templates, keyed by
 * provider key then variable name. Each variable holds an ordered list of
 * values; comma-separated strings bind to a list:
 *
 * <pre>
 * mqtt:
 *   topic-variables:
 *     av:
 *       "[publishAgency]": ${AV_PUBLISH_AGENCIES:DDOT}
 *       "[subscribeAgency]": ${AV_SUBSCRIBE_AGENCIES:DDOT,Raven}
 * </pre>
 *
 * Variable names are camelCase, so they are written in bracket notation to
 * keep their case when bound. These env/yml values are the defaults;
 * per-provider values set through the v3 admin API take precedence.
 */
@Data
@Component
@ConfigurationProperties(prefix = "mqtt")
public class MqttTopicProperties {

    private Map<String, Map<String, List<String>>> topicVariables = new LinkedHashMap<>();

    /**
     * @return the configured variables for the provider, or an empty map
     */
    public Map<String, List<String>> variablesFor(String providerKey) {
        Map<String, List<String>> variables = topicVariables.get(providerKey);
        if (variables == null) {
            return Map.of();
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        variables.forEach((name, values) -> copy.put(name, values == null ? List.of() : List.copyOf(values)));
        return copy;
    }
}
