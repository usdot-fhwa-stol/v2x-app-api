package usdot.v2x.app.api.keycloak.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses generated lab keys only; no historical private keys or live integrations. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = JwtKeyRotationTest.TestConfig.class)
@TestPropertySource(properties = "keycloak.clientId=v2x-app-api")
class JwtKeyRotationTest {
    private static final String ISSUER = "https://issuer.example/realms/v2x-app";
    private static final RSAKey TRUSTED = generateKey("trusted");
    private static final RSAKey RETIRED = generateKey("retired");
    private static final AtomicReference<RSAKey> PUBLISHED_KEY = new AtomicReference<>(TRUSTED);
    private static final HttpServer JWKS_SERVER = startJwksServer();

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtDecoder decoder;
    @Autowired
    private OAuth2ResourceServerProperties properties;
    @Autowired
    private List<OAuth2TokenValidator<Jwt>> validators;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        PUBLISHED_KEY.set(TRUSTED);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class))
                .build();
    }

    @AfterAll
    static void stopJwksServer() {
        JWKS_SERVER.stop(0);
    }

    @Test
    void trustedSignatureAndExistingAdminRoleAreAccepted() throws Exception {
        mvc.perform(get("/test/admin").header("Authorization", "Bearer " + token(TRUSTED, "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void unknownAndRetiredSignaturesReturnUnauthorized() throws Exception {
        mvc.perform(get("/test/admin").header("Authorization", "Bearer " + token(RETIRED, "ROLE_ADMIN")))
                .andExpect(status().isUnauthorized());
        // Even claiming the currently trusted key ID cannot make a retired signature valid.
        mvc.perform(get("/test/admin").header("Authorization", "Bearer "
                        + token(RETIRED, TRUSTED.getKeyID(), "ROLE_ADMIN")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void existingRoleRestrictionsStillApply() throws Exception {
        mvc.perform(get("/test/admin").header("Authorization", "Bearer " + token(TRUSTED, "ROLE_USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/test/admin").header("Authorization", "Bearer " + token(TRUSTED, "ROLE_DEPOSITOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void freshDecoderRejectsOldTokensAfterProviderRotation() throws Exception {
        String oldToken = token(TRUSTED, "ROLE_ADMIN");
        assertEquals("lab-user", decoder.decode(oldToken).getSubject());
        PUBLISHED_KEY.set(RETIRED);
        // Construct the production decoder again, as happens when an API instance restarts.
        JwtDecoder restarted = new JwtSecurityConfig().jwtDecoder(validators, properties);
        assertThrows(JwtException.class, () -> restarted.decode(oldToken));
        assertEquals("lab-user", restarted.decode(token(RETIRED, "ROLE_ADMIN")).getSubject());
    }

    private static RSAKey generateKey(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Unable to generate lab key", e);
        }
    }

    private static HttpServer startJwksServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/jwks", exchange -> {
                byte[] response = new JWKSet(PUBLISHED_KEY.get().toPublicJWK()).toString()
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(response);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to start lab JWKS server", e);
        }
    }

    private static String token(RSAKey key, String role) throws JOSEException {
        return token(key, key.getKeyID(), role);
    }

    private static String token(RSAKey key, String kid, String role) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(),
                new JWTClaimsSet.Builder().issuer(ISSUER).subject("lab-user")
                        .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                        .claim("realm_access", Map.of("roles", List.of(role))).build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    @Configuration
    @EnableWebMvc
    @Import({JwtSecurityConfig.class, KeycloakSecurityConfig.class, MethodSecurityConfig.class})
    static class TestConfig {
        @Bean
        OAuth2ResourceServerProperties resourceServerProperties() {
            var properties = new OAuth2ResourceServerProperties();
            properties.getJwt().setIssuerUri(ISSUER);
            properties.getJwt().setJwkSetUri("http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks");
            return properties;
        }

        @Bean
        LabController labController() {
            return new LabController();
        }
    }

    @RestController
    static class LabController {
        @GetMapping("/test/admin")
        @PreAuthorize("hasRole('ROLE_ADMIN')")
        public String admin() {
            return "ok";
        }
    }
}
