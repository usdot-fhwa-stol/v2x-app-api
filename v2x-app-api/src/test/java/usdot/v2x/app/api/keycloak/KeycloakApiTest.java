package usdot.v2x.app.api.keycloak;

import usdot.v2x.app.api.models.keycloak.TokenPostRequest;
import usdot.v2x.app.api.models.keycloak.TokenPostResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KeycloakApiTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private KeycloakProperties keycloakProperties;

    private KeycloakApi keycloakApi;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock KeycloakProperties
        when(keycloakProperties.getRealm()).thenReturn("test-realm");
        when(keycloakProperties.getClientId()).thenReturn("test-client-id");
        when(keycloakProperties.getClientSecret()).thenReturn("test-client-secret");
        when(keycloakProperties.getEndpoint()).thenReturn("http://localhost:8080");

        // Mock WebClient.Builder
        when(webClientBuilder.baseUrl(any(String.class))).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        // Mock WebClient
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);

        // Initialize KeycloakApi with mocked dependencies
        keycloakApi = new KeycloakApi(keycloakProperties, webClientBuilder);
    }

    @Test
    public void testGenerateKeycloakToken_Success() {
        TokenPostRequest request = new TokenPostRequest("username", "password");
        TokenPostResponse expectedResponse = new TokenPostResponse("access_token", 3600, 3600, "refresh_token",
                "token_type", "id_token", "not_before_policy", "session_state", "scope");

        when(requestHeadersSpec.exchangeToMono(any())).thenReturn(Mono.just(expectedResponse));

        Mono<TokenPostResponse> responseMono = keycloakApi.generateKeycloakToken(request);

        StepVerifier.create(responseMono)
                .expectNext(expectedResponse)
                .verifyComplete();

        // Verify method calls and parameters
        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/realms/test-realm/protocol/openid-connect/token");
        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestBodySpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED_VALUE, headers.getFirst(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testGenerateKeycloakToken_Error() {
        TokenPostRequest request = new TokenPostRequest("username", "password");
        String errorMessage = "Keycloak returned error: error-body";

        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.error(
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage)));

        Mono<TokenPostResponse> responseMono = keycloakApi.generateKeycloakToken(request);

        StepVerifier.create(responseMono)
                .expectErrorMatches(throwable -> throwable instanceof ResponseStatusException &&
                        ((ResponseStatusException) throwable)
                                .getStatusCode() == HttpStatus.BAD_REQUEST
                        &&
                        throwable.getMessage().contains(errorMessage))
                .verify();

        // Verify method calls and parameters
        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/realms/test-realm/protocol/openid-connect/token");
        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestBodySpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED_VALUE, headers.getFirst(HttpHeaders.CONTENT_TYPE));
    }
}