package usdot.v2x.app.api.config.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class MqttTopicPropertiesTest {

    private static MqttTopicProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("mqtt", MqttTopicProperties.class);
    }

    @Test
    void bindsProviderTopicVariables() {
        MqttTopicProperties properties = bind(Map.of("mqtt.topic-variables.av.agency", "DDOT"));

        assertEquals(Map.of("agency", "DDOT"), properties.variablesFor("av"));
    }

    @Test
    void unknownProviderHasNoVariables() {
        assertTrue(bind(Map.of()).variablesFor("nmi").isEmpty());
    }
}
