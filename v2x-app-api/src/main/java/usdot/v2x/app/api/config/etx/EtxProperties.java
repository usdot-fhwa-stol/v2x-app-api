package usdot.v2x.app.api.config.etx;

import lombok.Data;
import usdot.v2x.app.api.models.etx.configuration.geofence.DistributionType;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration properties for ETX integration.
 */
@Configuration
@ConfigurationProperties(prefix = "etx")
@Data
public class EtxProperties {
    private Boolean enabled;
    private String endpoint;
    private String vendorId;
    private String depositorVendorId;
    private String username;
    private String password;
    private RegistrationLimits registration = new RegistrationLimits();
    private Configuration configuration = new Configuration();

    @Data
    public static class RegistrationLimits {
        private List<VendorConfig> vendors;
    }

    @Data
    public static class VendorConfig {
        private String id;
        private int limit;
        private int userLimit;
    }

    @Data
    public static class Configuration {
        private DistributionType distributionType;
        private ConfigurationCleanup cleanup;
    }

    @Data
    public static class ConfigurationCleanup {
        private boolean enabled;
        private int intervalMinutes;
    }
}
