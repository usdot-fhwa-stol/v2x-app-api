package usdot.v2x.app.api.keycloak;

import usdot.v2x.app.api.models.keycloak.TokenPostRequest;
import usdot.v2x.app.api.models.keycloak.TokenPostRequestKeycloak;
import usdot.v2x.app.api.models.keycloak.TokenPostResponse;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class KeycloakApi {
    private final String keycloakRealm;
    private final String keycloakClientId;
    private final String keycloakClientSecret;

    private final WebClient webClient;

    public KeycloakApi(KeycloakProperties keycloakProperties, WebClient.Builder webClientBuilder) {
        this.keycloakRealm = keycloakProperties.getRealm();
        this.keycloakClientId = keycloakProperties.getClientId();
        this.keycloakClientSecret = keycloakProperties.getClientSecret();
        this.webClient = webClientBuilder.baseUrl(keycloakProperties.getEndpoint()).build();
    }

    public Mono<TokenPostResponse> generateKeycloakToken(TokenPostRequest request) {
        TokenPostRequestKeycloak requestBody = new TokenPostRequestKeycloak(request, keycloakClientId,
                keycloakClientSecret, "password", "openid");
        return webClient.post()
                .uri(String.format("/realms/%s/protocol/openid-connect/token", keycloakRealm))
                .headers(headers -> {
                    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE);
                })
                .body(BodyInserters.fromFormData(requestBody.getFormData()))
                .exchangeToMono(response -> switch (response.statusCode()) {
                    case HttpStatus.OK -> response.bodyToMono(TokenPostResponse.class);
                    default -> {
                        log.warn("Received non-success error code: {}", response.statusCode());
                        yield response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new ResponseStatusException(response.statusCode(), body)));
                    }
                });
    }
}
