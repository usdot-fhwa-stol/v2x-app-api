package usdot.v2x.app.api.keycloak.config;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import usdot.v2x.app.api.keycloak.support.CorsUtil;
import usdot.v2x.app.api.keycloak.support.KeycloakJwtAuthenticationConverter;

/**
 * Provides keycloak based spring security configuration.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class KeycloakSecurityConfig {

    @Value("${keycloak.clientId}")
    private String clientId;

    final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {

        return httpSecurity
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(corsConfigurer -> CorsUtil.configureCors(corsConfigurer, "*"))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(request -> request
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // Allow CORS preflight
                        .requestMatchers("/actuator/health").permitAll() // Allow health checks
                        .requestMatchers("/actuator/metrics").permitAll() // Allow metrics
                        .requestMatchers("/actuator/prometheus").permitAll() // Allow prometheus
                        .requestMatchers("/api/v2/decode/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/token").permitAll() // Allow swagger-ui docs
                        .requestMatchers("/**").access(AccessController::checkAccess)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServerConfigurer -> resourceServerConfigurer.jwt(
                        jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter)))
                .build();
    }

    @Bean
    AccessController accessController() {
        return new AccessController();
    }
}