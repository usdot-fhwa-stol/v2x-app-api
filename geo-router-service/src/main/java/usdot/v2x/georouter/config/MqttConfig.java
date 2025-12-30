package usdot.v2x.georouter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for MQTT broker connection.
 * Uses a single broker for both subscribing to incoming BSM messages
 * and publishing routed messages to subscribers.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mqtt")
public class MqttConfig {

    private String brokerUrl = "tcp://localhost:1883";
    private String clientId = "geo-router-service";
    private String username;
    private String password;

    // Topic for receiving BSM messages from devices (ingress)
    private String subscribeTopic = "v2x/bsm/publish";

    // Topic pattern for subscribing to geohash-filtered messages from TMC apps
    private String geohashFilteredTopicPattern = "v2x/georelevance/+/+/+/+/+/+/+/BSM";

    // Topic pattern for publishing directly to clients (egress)
    // Format: v2x/client/{deviceId}/messages
    private String clientEgressTopicPattern = "v2x/client/{deviceId}/messages";

    private int qos = 1;
    private boolean cleanSession = true;
    private int connectionTimeout = 30;
    private int keepAliveInterval = 60;
    private boolean autoReconnect = true;
}
