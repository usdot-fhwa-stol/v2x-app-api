package usdot.v2x.app.api.etx;

import usdot.v2x.app.api.config.etx.EtxProperties;
import usdot.v2x.app.api.config.etx.ThingspaceProperties;
import usdot.v2x.app.api.models.etx.AuthToken;
import usdot.v2x.app.api.models.etx.SessionToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;

@Slf4j
@Component
public class ThingspaceApi {
    private final String etxUsername;
    private final String etxPassword;
    private final WebClient webClient;
    private final String encodedThingspaceCredentials;

    public ThingspaceApi(
            EtxProperties etxProperties,
            ThingspaceProperties thingspaceProperties,
            WebClient.Builder webClientBuilder) {
        this.etxUsername = etxProperties.getUsername();
        this.etxPassword = etxProperties.getPassword();
        this.webClient = webClientBuilder.baseUrl(thingspaceProperties.getEndpoint()).build();
        this.encodedThingspaceCredentials = encodeClientCredentials(thingspaceProperties.getKey(),
                thingspaceProperties.getSecret());
    }

    private String encodeClientCredentials(String clientKey, String clientSecret) {
        return Base64.getEncoder().encodeToString((clientKey + ":" + clientSecret).getBytes());
    }

    public Mono<AuthToken> generateAccessToken() {
        return webClient.post()
                .uri("/api/ts/v1/oauth2/token")
                .headers(headers -> {
                    headers.set("Authorization", "Basic " + encodedThingspaceCredentials);
                    headers.set("Content-Type", "application/x-www-form-urlencoded");
                })
                .bodyValue("grant_type=client_credentials")
                .retrieve()
                .bodyToMono(AuthToken.class)
                .retryWhen(usdot.v2x.app.api.config.WebClientConfig.getRetrySpec())
                .doOnError(error -> log.error("Failed to generate access token after retries: {}", error.getMessage()));
    }

    public Mono<SessionToken> generateSessionToken(String accessToken) {
        return webClient.post()
                .uri("/api/m2m/v1/session/login")
                .headers(headers -> {
                    headers.set("Authorization", "Bearer " + accessToken);
                    headers.set("Content-Type", "application/json");
                })
                .bodyValue("{" +
                        "\"username\":\"" + etxUsername + "\"," +
                        "\"password\":\"" + etxPassword + "\"}" // Adjust payload as needed
                )
                .retrieve()
                .bodyToMono(SessionToken.class)
                .retryWhen(usdot.v2x.app.api.config.WebClientConfig.getRetrySpec())
                .doOnError(
                        error -> log.error("Failed to generate session token after retries: {}", error.getMessage()));
    }
}
