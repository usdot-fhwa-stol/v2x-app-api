package usdot.v2x.app.api.config.provider;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Default values for whole-level topic variables (for example {@code {agency}})
 * used in provider ACL topic templates, keyed by provider key then variable
 * name:
 *
 * <pre>
 * mqtt:
 *   topic-variables:
 *     av:
 *       agency: ${AV_TOPIC_AGENCY:DDOT}
 * </pre>
 *
 * These env/yml values are the defaults. Per-provider values set later through
 * the v3 admin API take precedence.
 */
@Data
@Component
@ConfigurationProperties(prefix = "mqtt")
public class MqttTopicProperties {

    private Map<String, Map<String, String>> topicVariables = new LinkedHashMap<>();

    /**
     * @return the configured variables for the provider, or an empty map
     */
    public Map<String, String> variablesFor(String providerKey) {
        Map<String, String> variables = topicVariables.get(providerKey);
        return variables == null ? Map.of() : Map.copyOf(variables);
    }
}
