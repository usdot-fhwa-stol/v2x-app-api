package usdot.v2x.app.api.keycloak.support;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Provides utility methods for configuring CORS
 */
public class CorsUtil {

    /**
     * Configures CORS
     *
     * @param cors mutable cors configuration
     *
     *
     */
    public static void configureCors(CorsConfigurer<HttpSecurity> cors, String corsAllowedOrigin) {

        UrlBasedCorsConfigurationSource defaultUrlBasedCorsConfigSource = new UrlBasedCorsConfigurationSource();
        CorsConfiguration corsConfiguration = new CorsConfiguration().applyPermitDefaultValues();
        corsConfiguration.addAllowedOrigin(corsAllowedOrigin);
        List.of("GET", "POST", "PUT", "DELETE", "OPTIONS").forEach(corsConfiguration::addAllowedMethod);
        defaultUrlBasedCorsConfigSource.registerCorsConfiguration("/**", corsConfiguration);

        cors.configurationSource(req -> {

            CorsConfiguration config = new CorsConfiguration();

            config = config.combine(defaultUrlBasedCorsConfigSource.getCorsConfiguration(req));
            return config;
        });
    }
}
