package usdot.v2x.app.api.config.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

class MqttTopicPropertiesTest {

    private static MqttTopicProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("mqtt", MqttTopicProperties.class);
    }

    @Test
    void bindsCommaSeparatedValuesAsOrderedList() {
        MqttTopicProperties properties = bind(Map.of(
                "mqtt.topic-variables.av[publishAgency]", "DDOT",
                "mqtt.topic-variables.av[subscribeAgency]", "DDOT,Raven"));

        assertEquals(Map.of(
                "publishAgency", List.of("DDOT"),
                "subscribeAgency", List.of("DDOT", "Raven")),
                properties.variablesFor("av"));
    }

    @Test
    void unknownProviderHasNoVariables() {
        assertTrue(bind(Map.of()).variablesFor("nmi").isEmpty());
    }

    @Test
    void applicationYmlDefaultsPublishToDdotAndSubscribeToDdotAndRaven() throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(sources::addLast);
        Binder binder = new Binder(ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources));

        MqttTopicProperties properties = binder.bindOrCreate("mqtt", MqttTopicProperties.class);

        assertEquals(List.of("DDOT"), properties.variablesFor("av").get("publishAgency"));
        assertEquals(List.of("DDOT", "Raven"), properties.variablesFor("av").get("subscribeAgency"));
    }
}
