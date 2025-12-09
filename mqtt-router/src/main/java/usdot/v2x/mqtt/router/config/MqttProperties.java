package usdot.v2x.mqtt.router.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {
    private Broker broker = new Broker();
    private int qos = 1;
    private boolean retain = true;

    @Data
    public static class Broker {
        private String host = "localhost";
        private int port = 1883;
        private String username;
        private String password;

        public String getConnectionUrl() {
            return "tcp://" + host + ":" + port;
        }
    }
}
