package usdot.v2x.app.api.etx.registration;

import usdot.v2x.app.api.config.etx.EtxProperties;
import usdot.v2x.app.api.etx.TokenService;
import usdot.v2x.app.api.etx.TokenStore;
import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.etx.ErrorResponseException;
import usdot.v2x.app.api.models.etx.registration.*;
import usdot.v2x.app.api.utils.SecurityContextUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RegistrationApiTest {

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
    private ClientResponse responseSpec;

    @Mock
    private TokenService tokenService;

    @Mock
    private TokenStore tokenStore;

    @Mock
    private EtxProperties etxProperties;

    @Mock
    private SecurityContextUtils securityContextUtils;

    private RegistrationApi registrationApi;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock EtxProperties
        when(etxProperties.getVendorId()).thenReturn("test-vendor-id");
        when(etxProperties.getUsername()).thenReturn("test-username");
        when(etxProperties.getPassword()).thenReturn("test-password");
        when(etxProperties.getEndpoint()).thenReturn("http://localhost:8080");

        // Mock TokenService
        when(tokenService.getTokenStore()).thenReturn(Mono.just(tokenStore));
        when(tokenStore.getAccessToken()).thenReturn("access-token");
        when(tokenStore.getSessionToken()).thenReturn("session-token");

        // Mock WebClient.Builder
        when(webClientBuilder.baseUrl(any(String.class))).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        // Mock WebClient
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(webClient.put()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);

        // Mock SecurityContextUtils
        when(securityContextUtils.determineVendorId()).thenReturn("test-vendor-id");

        // Initialize RegistrationApi with mocked dependencies
        registrationApi = new RegistrationApi(etxProperties, tokenService, webClientBuilder, securityContextUtils);
    }

    @Test
    public void testClientRegistrationPost_Success() {
        RegistrationPostRequest request = new RegistrationPostRequest();
        RegistrationResponse expectedResponse = new RegistrationResponse();

        when(requestHeadersSpec.exchangeToMono(any())).thenReturn(Mono.just(expectedResponse));

        Mono<RegistrationResponse> responseMono = registrationApi.clientRegistrationPost(request);

        StepVerifier.create(responseMono)
                .expectNext(expectedResponse)
                .verifyComplete();

        // Verify method calls and parameters
        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/api/v2/clients/registration");
        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestBodySpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals(MediaType.APPLICATION_JSON_VALUE, headers.getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals("Bearer access-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("session-token", headers.getFirst("SessionToken"));
    }

    @Test
    public void testClientRegistrationPost_Error() {
        RegistrationPostRequest request = new RegistrationPostRequest();
        ErrorResponse errorResponse = new ErrorResponse("error", "description");

        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.error(new ErrorResponseException(errorResponse,
                        HttpStatus.INTERNAL_SERVER_ERROR)));

        Mono<RegistrationResponse> responseMono = registrationApi.clientRegistrationPost(request);

        StepVerifier.create(responseMono)
                .expectError(ErrorResponseException.class)
                .verify();

        // Verify method calls and parameters
        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/api/v2/clients/registration");
        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestBodySpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals(MediaType.APPLICATION_JSON_VALUE, headers.getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals("Bearer access-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("session-token", headers.getFirst("SessionToken"));
    }

    @Test
    public void testClientRegistrationPut_Success() {
        RegistrationPutRequest request = new RegistrationPutRequest();
        RegistrationResponse expectedResponse = new RegistrationResponse();

        when(requestBodySpec.exchangeToMono(any())).thenReturn(Mono.just(expectedResponse));

        Mono<RegistrationResponse> responseMono = registrationApi.clientRegistrationPut(request);

        StepVerifier.create(responseMono)
                .expectNext(expectedResponse)
                .verifyComplete();

        // Verify method calls and parameters
        verify(webClient).put();
        verify(requestBodyUriSpec).uri("/api/v2/clients/registration");
        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestBodySpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals(MediaType.APPLICATION_JSON_VALUE, headers.getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals("Bearer access-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("session-token", headers.getFirst("SessionToken"));
        assertEquals("test-vendor-id", headers.getFirst("VendorID"));
        assertEquals(request.getDeviceId(), headers.getFirst("DeviceID"));
    }

    @Test
    public void testClientRegistrationPut_Error() {
        RegistrationPutRequest request = new RegistrationPutRequest();
        ErrorResponse errorResponse = new ErrorResponse("error", "description");

        when(requestBodySpec.exchangeToMono(any()))
                .thenReturn(Mono.error(new ErrorResponseException(errorResponse,
                        HttpStatus.INTERNAL_SERVER_ERROR)));

        Mono<RegistrationResponse> responseMono = registrationApi.clientRegistrationPut(request);

        StepVerifier.create(responseMono)
                .expectError(ErrorResponseException.class)
                .verify();

        // Verify method calls and parameters
        verify(webClient).put();
        verify(requestBodyUriSpec).uri("/api/v2/clients/registration");
        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestBodySpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals(MediaType.APPLICATION_JSON_VALUE, headers.getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals("Bearer access-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("session-token", headers.getFirst("SessionToken"));
        assertEquals("test-vendor-id", headers.getFirst("VendorID"));
        assertEquals(request.getDeviceId(), headers.getFirst("DeviceID"));
    }

    @Test
    public void testClientConnectionPost_Success() {
        ConnectionPostRequest request = new ConnectionPostRequest();
        request.setLat(0.0); // Set required fields to avoid NullPointerException
        request.setLon(0.0); // Set required fields to avoid NullPointerException
        ConnectionResponse expectedResponse = new ConnectionResponse();

        when(requestHeadersSpec.exchangeToMono(any())).thenReturn(Mono.just(expectedResponse));

        Mono<ConnectionResponse> responseMono = registrationApi.clientConnectionPost(request);

        StepVerifier.create(responseMono)
                .expectNext(expectedResponse)
                .verifyComplete();

        // Verify method calls and parameters
        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/api/v2/clients/connection");
        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestBodySpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals(MediaType.APPLICATION_JSON_VALUE, headers.getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals("Bearer access-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("session-token", headers.getFirst("SessionToken"));
        assertEquals("test-vendor-id", headers.getFirst("VendorID"));
    }

    @Test
    public void testClientConnectionPost_Error() {
        ConnectionPostRequest request = new ConnectionPostRequest();
        request.setLat(0.0); // Set required fields to avoid NullPointerException
        request.setLon(0.0); // Set required fields to avoid NullPointerException
        ErrorResponse errorResponse = new ErrorResponse("error", "description");

        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.error(new ErrorResponseException(errorResponse,
                        HttpStatus.INTERNAL_SERVER_ERROR)));

        Mono<ConnectionResponse> responseMono = registrationApi.clientConnectionPost(request);

        StepVerifier.create(responseMono)
                .expectError(ErrorResponseException.class)
                .verify();

        // Verify method calls and parameters
        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/api/v2/clients/connection");
        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestBodySpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals(MediaType.APPLICATION_JSON_VALUE, headers.getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals("Bearer access-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("session-token", headers.getFirst("SessionToken"));
        assertEquals("test-vendor-id", headers.getFirst("VendorID"));
    }
}