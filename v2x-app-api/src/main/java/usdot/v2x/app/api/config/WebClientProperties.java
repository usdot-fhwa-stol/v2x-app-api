package usdot.v2x.app.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for WebClient setup.
 */
@Data
@Component
@ConfigurationProperties(prefix = "webclient")
public class WebClientProperties {

    private Timeout timeout = new Timeout();
    private Retry retry = new Retry();

    @Data
    public static class Timeout {
        private int connect = 5000;
        private int read = 30000;
        private int write = 10000;
    }

    @Data
    public static class Retry {
        private int maxAttempts = 3;
        private Backoff backoff = new Backoff();

        @Data
        public static class Backoff {
            private long initialDelay = 1000;
            private long maxDelay = 10000;
            private double multiplier = 2.0;
        }
    }
}
