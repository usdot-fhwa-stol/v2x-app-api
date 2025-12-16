package usdot.v2x.app.api.etx.configuration;

import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import usdot.v2x.app.api.models.etx.configuration.DepositRequest;
import usdot.v2x.app.api.models.etx.configuration.ConfigurationGeofence;
import usdot.v2x.app.api.models.etx.configuration.ConfigurationGeofenceResponse;
import usdot.v2x.app.api.models.etx.configuration.ConfigurationGeofenceSummary;
import usdot.v2x.app.api.models.etx.configuration.geofence.DistributionType;
import usdot.v2x.app.api.utils.TimCoordinateConverter;
import usdot.v2x.app.api.utils.MapCoordinateConverter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.GeometryFactory;

import j2735ffm.MessageFrameCodec;

import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.etx.ErrorResponseException;
import com.fasterxml.jackson.databind.ObjectMapper;

import usdot.v2x.app.api.config.etx.EtxProperties;
import usdot.v2x.app.api.etx.TokenService;
import usdot.v2x.app.api.etx.TokenStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.UniqueMSGID;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrameList;
import us.dot.its.jpo.asn.j2735.r2024.Common.DYear;
import us.dot.its.jpo.asn.j2735.r2024.Common.MinuteOfTheYear;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.MinutesDuration;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.n52.jackson.datatype.jts.JtsModule;

import usdot.v2x.app.api.models.etx.configuration.ConfigurationClearGeofence;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeature;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;

public class ConfigurationApiTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private TokenService tokenService;

    @Mock
    private TokenStore tokenStore;

    @Mock
    private EtxProperties etxProperties;

    private ConfigurationApi configurationApi;

    private ObjectMapper objectMapper;
    private XmlMapper xmlMapper;

    @Mock
    private MessageFrameCodec codec;

    @Mock
    private TimCoordinateConverter timConverter;

    @Mock
    private MapCoordinateConverter mapConverter;

    @Mock
    private Clock clock;

    @Mock
    private EtxProperties.Configuration configuration;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Initialize ObjectMapper with JTS support
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JtsModule());
        objectMapper.setSerializationInclusion(Include.NON_NULL);

        // Initialize XmlMapper
        xmlMapper = new XmlMapper();

        // Mock EtxProperties
        when(etxProperties.getVendorId()).thenReturn("test-vendor-id");
        when(etxProperties.getUsername()).thenReturn("test-username");
        when(etxProperties.getPassword()).thenReturn("test-password");
        when(etxProperties.getEndpoint()).thenReturn("http://localhost:8080");
        when(etxProperties.getConfiguration()).thenReturn(configuration);
        when(configuration.getDistributionType()).thenReturn(DistributionType.Targeted);

        // Mock TokenService
        when(tokenService.getTokenStore()).thenReturn(Mono.just(tokenStore));
        when(tokenStore.getAccessToken()).thenReturn("access-token");
        when(tokenStore.getSessionToken()).thenReturn("session-token");

        // Mock WebClient.Builder
        when(webClientBuilder.baseUrl(any(String.class))).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        // Mock WebClient
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(webClient.put()).thenReturn(requestBodyUriSpec);
        when(webClient.delete()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(String.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
        when(requestHeadersSpec.headers(any())).thenReturn(requestHeadersSpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);

        // Initialize ConfigurationApi with mocked dependencies
        configurationApi = new ConfigurationApi(etxProperties, tokenService, webClientBuilder, codec,
                objectMapper, xmlMapper, clock, timConverter, mapConverter);
    }

    @Test
    public void testGetGeofences_Success() {
        List<ConfigurationGeofenceSummary> expectedResponse = List.of(new ConfigurationGeofenceSummary());

        when(requestHeadersSpec.exchangeToMono(any())).thenReturn(Mono.just(expectedResponse));

        Mono<List<ConfigurationGeofenceSummary>> responseMono = configurationApi.getGeofences();

        StepVerifier.create(responseMono)
                .expectNext(expectedResponse)
                .verifyComplete();

        // Verify method calls and parameters
        verify(webClient).get();
        verify(requestHeadersUriSpec).uri("/geofence/ids");
        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestHeadersSpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals("Bearer access-token", headers.getFirst("Authorization"));
        assertEquals("session-token", headers.getFirst("SessionToken"));
        assertEquals("test-vendor-id", headers.getFirst("VendorID"));
        assertEquals("application/json", headers.getFirst("Accept"));
    }

    @Test
    public void testGetGeofences_Error() {
        ErrorResponse errorResponse = new ErrorResponse("error", "description");

        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.error(new ErrorResponseException(errorResponse,
                        HttpStatus.INTERNAL_SERVER_ERROR)));

        Mono<List<ConfigurationGeofenceSummary>> responseMono = configurationApi.getGeofences();

        StepVerifier.create(responseMono)
                .expectError(ErrorResponseException.class)
                .verify();

        // Verify method calls and parameters
        verify(webClient).get();
        verify(requestHeadersUriSpec).uri("/geofence/ids");
        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestHeadersSpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals("Bearer access-token", headers.getFirst("Authorization"));
        assertEquals("session-token", headers.getFirst("SessionToken"));
        assertEquals("test-vendor-id", headers.getFirst("VendorID"));
        assertEquals("application/json", headers.getFirst("Accept"));
    }

    @Test
    public void testGetGeofence_Success() {
        ConfigurationGeofenceResponse expectedResponse = new ConfigurationGeofenceResponse();

        when(requestHeadersSpec.exchangeToMono(any())).thenReturn(Mono.just(expectedResponse));

        Mono<ConfigurationGeofenceResponse> responseMono = configurationApi.getGeofence("geofence-id");

        StepVerifier.create(responseMono)
                .expectNext(expectedResponse)
                .verifyComplete();

        // Verify method calls and parameters
        verify(webClient).get();
        ArgumentCaptor<Function<UriBuilder, URI>> uriFunctionCaptor = ArgumentCaptor.forClass(Function.class);
        verify(requestHeadersUriSpec).uri(uriFunctionCaptor.capture());
        Function<UriBuilder, URI> uriFunction = uriFunctionCaptor.getValue();
        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder();
        URI uri = uriFunction.apply(uriBuilder);
        assertEquals("/geofence?id=geofence-id", uri.toString());

        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestHeadersSpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals("Bearer access-token", headers.getFirst("Authorization"));
        assertEquals("session-token", headers.getFirst("SessionToken"));
        assertEquals("test-vendor-id", headers.getFirst("VendorID"));
        assertEquals("application/json", headers.getFirst("Content-Type"));
        assertEquals("application/json", headers.getFirst("Accept"));
    }

    @Test
    public void testGetGeofence_Error() {
        ErrorResponse errorResponse = new ErrorResponse("error", "description");

        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.error(new ErrorResponseException(errorResponse,
                        HttpStatus.INTERNAL_SERVER_ERROR)));

        Mono<ConfigurationGeofenceResponse> responseMono = configurationApi.getGeofence("geofence-id");

        StepVerifier.create(responseMono)
                .expectError(ErrorResponseException.class)
                .verify();

        // Verify method calls and parameters
        verify(webClient).get();
        ArgumentCaptor<Function<UriBuilder, URI>> uriFunctionCaptor = ArgumentCaptor.forClass(Function.class);
        verify(requestHeadersUriSpec).uri(uriFunctionCaptor.capture());
        Function<UriBuilder, URI> uriFunction = uriFunctionCaptor.getValue();
        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder();
        URI uri = uriFunction.apply(uriBuilder);
        assertEquals("/geofence?id=geofence-id", uri.toString());

        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestHeadersSpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals("Bearer access-token", headers.getFirst("Authorization"));
        assertEquals("session-token", headers.getFirst("SessionToken"));
        assertEquals("test-vendor-id", headers.getFirst("VendorID"));
        assertEquals("application/json", headers.getFirst("Content-Type"));
        assertEquals("application/json", headers.getFirst("Accept"));
    }

    @Test
    public void testCreateGeofence_Success() throws Exception {
        // Load test configuration from JSON file
        ConfigurationGeofence geofence = objectMapper.readValue(
                getClass().getResourceAsStream(
                        "/usdot/v2x/app/api/etx/configuration/Configuration.json"),
                ConfigurationGeofence.class);
        ConfigurationGeofenceResponse expectedResponse = new ConfigurationGeofenceResponse();

        when(requestHeadersSpec.exchangeToMono(any())).thenReturn(Mono.just(expectedResponse));
        when(requestBodySpec.bodyValue(any(String.class))).thenReturn(requestHeadersSpec);

        Mono<ConfigurationGeofenceResponse> responseMono = configurationApi.createGeofence(geofence);

        StepVerifier.create(responseMono)
                .expectNext(expectedResponse)
                .verifyComplete();

        // Verify method calls and parameters
        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/geofence");
        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestBodySpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals("Bearer access-token", headers.getFirst("Authorization"));
        assertEquals("session-token", headers.getFirst("SessionToken"));
        assertEquals("test-vendor-id", headers.getFirst("VendorID"));
        assertEquals("application/json", headers.getFirst("Content-Type"));
        assertEquals("application/json", headers.getFirst("Accept"));

        // Verify the JSON string being sent
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodySpec).bodyValue(bodyCaptor.capture());

        // Compare the JSON strings (after parsing to ignore formatting differences)
        JsonNode actualJson = objectMapper.readTree(bodyCaptor.getValue());
        JsonNode expectedJson = objectMapper.readTree(
                getClass().getResourceAsStream(
                        "/usdot/v2x/app/api/etx/configuration/Configuration.json"));

        assertThat(actualJson, jsonEquals(expectedJson)
                .withTolerance(0.0001));
    }

    @Test
    public void testCreateGeofence_Error() throws Exception {
        // Load test configuration from JSON file
        ConfigurationGeofence geofence = objectMapper.readValue(
                getClass().getResourceAsStream(
                        "/usdot/v2x/app/api/etx/configuration/Configuration.json"),
                ConfigurationGeofence.class);
        ErrorResponse errorResponse = new ErrorResponse("error", "description");

        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.error(new ErrorResponseException(errorResponse,
                        HttpStatus.INTERNAL_SERVER_ERROR)));
        when(requestBodySpec.bodyValue(any(String.class))).thenReturn(requestHeadersSpec);

        Mono<ConfigurationGeofenceResponse> responseMono = configurationApi.createGeofence(geofence);

        StepVerifier.create(responseMono)
                .expectError(ErrorResponseException.class)
                .verify();

        // Verify method calls and parameters
        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/geofence");
        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestBodySpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals("Bearer access-token", headers.getFirst("Authorization"));
        assertEquals("session-token", headers.getFirst("SessionToken"));
        assertEquals("test-vendor-id", headers.getFirst("VendorID"));
        assertEquals("application/json", headers.getFirst("Content-Type"));
        assertEquals("application/json", headers.getFirst("Accept"));

        // Verify the JSON string being sent
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodySpec).bodyValue(bodyCaptor.capture());
        JsonNode actualJson = objectMapper.readTree(bodyCaptor.getValue());
        JsonNode expectedJson = objectMapper.readTree(
                getClass().getResourceAsStream(
                        "/usdot/v2x/app/api/etx/configuration/Configuration.json"));

        assertThat(actualJson, jsonEquals(expectedJson)
                .withTolerance(0.0001));
    }

    @Test
    public void testUpdateGeofence_Success() throws Exception {
        // Load test configuration from JSON file
        ConfigurationGeofence geofence = objectMapper.readValue(
                getClass().getResourceAsStream(
                        "/usdot/v2x/app/api/etx/configuration/Configuration.json"),
                ConfigurationGeofence.class);
        ConfigurationGeofenceResponse expectedResponse = new ConfigurationGeofenceResponse();

        when(requestHeadersSpec.exchangeToMono(any())).thenReturn(Mono.just(expectedResponse));
        when(requestBodySpec.bodyValue(any(String.class))).thenReturn(requestHeadersSpec);

        Mono<ConfigurationGeofenceResponse> responseMono = configurationApi.updateGeofence("geofence-id",
                geofence);

        StepVerifier.create(responseMono)
                .expectNext(expectedResponse)
                .verifyComplete();

        // Verify method calls and parameters
        verify(webClient).put();
        ArgumentCaptor<Function<UriBuilder, URI>> uriFunctionCaptor = ArgumentCaptor.forClass(Function.class);
        verify(requestBodyUriSpec).uri(uriFunctionCaptor.capture());
        Function<UriBuilder, URI> uriFunction = uriFunctionCaptor.getValue();
        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder();
        URI uri = uriFunction.apply(uriBuilder);
        assertEquals("/geofence?id=geofence-id", uri.toString());

        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestBodySpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals("Bearer access-token", headers.getFirst("Authorization"));
        assertEquals("session-token", headers.getFirst("SessionToken"));
        assertEquals("test-vendor-id", headers.getFirst("VendorID"));
        assertEquals("application/json", headers.getFirst("Content-Type"));
        assertEquals("application/json", headers.getFirst("Accept"));

        // Verify the JSON string being sent
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodySpec).bodyValue(bodyCaptor.capture());
        JsonNode actualJson = objectMapper.readTree(bodyCaptor.getValue());
        JsonNode expectedJson = objectMapper.readTree(
                getClass().getResourceAsStream(
                        "/usdot/v2x/app/api/etx/configuration/Configuration.json"));

        assertThat(actualJson, jsonEquals(expectedJson)
                .withTolerance(0.0001));
    }

    @Test
    public void testUpdateGeofence_Error() throws Exception {
        // Load test configuration from JSON file
        ConfigurationGeofence geofence = objectMapper.readValue(
                getClass().getResourceAsStream(
                        "/usdot/v2x/app/api/etx/configuration/Configuration.json"),
                ConfigurationGeofence.class);
        ErrorResponse errorResponse = new ErrorResponse("error", "description");

        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.error(new ErrorResponseException(errorResponse,
                        HttpStatus.INTERNAL_SERVER_ERROR)));
        when(requestBodySpec.bodyValue(any(String.class))).thenReturn(requestHeadersSpec);

        Mono<ConfigurationGeofenceResponse> responseMono = configurationApi.updateGeofence("geofence-id",
                geofence);

        StepVerifier.create(responseMono)
                .expectError(ErrorResponseException.class)
                .verify();

        // Verify method calls and parameters
        verify(webClient).put();
        ArgumentCaptor<Function<UriBuilder, URI>> uriFunctionCaptor = ArgumentCaptor.forClass(Function.class);
        verify(requestBodyUriSpec).uri(uriFunctionCaptor.capture());
        Function<UriBuilder, URI> uriFunction = uriFunctionCaptor.getValue();
        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder();
        URI uri = uriFunction.apply(uriBuilder);
        assertEquals("/geofence?id=geofence-id", uri.toString());

        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestBodySpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals("Bearer access-token", headers.getFirst("Authorization"));
        assertEquals("session-token", headers.getFirst("SessionToken"));
        assertEquals("test-vendor-id", headers.getFirst("VendorID"));
        assertEquals("application/json", headers.getFirst("Content-Type"));
        assertEquals("application/json", headers.getFirst("Accept"));

        // Verify the JSON string being sent
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodySpec).bodyValue(bodyCaptor.capture());
        JsonNode actualJson = objectMapper.readTree(bodyCaptor.getValue());
        JsonNode expectedJson = objectMapper.readTree(
                getClass().getResourceAsStream(
                        "/usdot/v2x/app/api/etx/configuration/Configuration.json"));

        assertThat(actualJson, jsonEquals(expectedJson)
                .withTolerance(0.0001));
    }

    @Test
    public void testDeleteGeofence_Success() {
        when(requestHeadersSpec.exchangeToMono(any())).thenReturn(Mono.empty());

        Mono<Void> responseMono = configurationApi.deleteGeofence("geofence-id");

        StepVerifier.create(responseMono)
                .verifyComplete();

        // Verify method calls and parameters
        verify(webClient).delete();
        ArgumentCaptor<Function<UriBuilder, URI>> uriFunctionCaptor = ArgumentCaptor.forClass(Function.class);
        verify(requestHeadersUriSpec).uri(uriFunctionCaptor.capture());
        Function<UriBuilder, URI> uriFunction = uriFunctionCaptor.getValue();
        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder();
        URI uri = uriFunction.apply(uriBuilder);
        assertEquals("/geofence?id=geofence-id", uri.toString());

        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestHeadersSpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals("Bearer access-token", headers.getFirst("Authorization"));
        assertEquals("session-token", headers.getFirst("SessionToken"));
        assertEquals("test-vendor-id", headers.getFirst("VendorID"));
        assertEquals("application/json", headers.getFirst("Content-Type"));
        assertEquals("application/json", headers.getFirst("Accept"));
    }

    @Test
    public void testDeleteGeofence_Error() {
        ErrorResponse errorResponse = new ErrorResponse("error", "description");

        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.error(new ErrorResponseException(errorResponse,
                        HttpStatus.INTERNAL_SERVER_ERROR)));

        Mono<Void> responseMono = configurationApi.deleteGeofence("geofence-id");

        StepVerifier.create(responseMono)
                .expectError(ErrorResponseException.class)
                .verify();

        // Verify method calls and parameters
        verify(webClient).delete();
        ArgumentCaptor<Function<UriBuilder, URI>> uriFunctionCaptor = ArgumentCaptor.forClass(Function.class);
        verify(requestHeadersUriSpec).uri(uriFunctionCaptor.capture());
        Function<UriBuilder, URI> uriFunction = uriFunctionCaptor.getValue();
        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder();
        URI uri = uriFunction.apply(uriBuilder);
        assertEquals("/geofence?id=geofence-id", uri.toString());

        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestHeadersSpec).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertEquals("Bearer access-token", headers.getFirst("Authorization"));
        assertEquals("session-token", headers.getFirst("SessionToken"));
        assertEquals("test-vendor-id", headers.getFirst("VendorID"));
        assertEquals("application/json", headers.getFirst("Content-Type"));
        assertEquals("application/json", headers.getFirst("Accept"));
    }

    @Test
    public void testDeposit_TIM_Success() throws Exception {
        DepositRequest request = new DepositRequest();
        request.setAsn1Hex(
                "001F647010F1E08D442EF0020C6B1A090F775D9B0301C26E26E4F965C75337FFF93F4878F07080A007F92A7BB7F741234E7D1B30EEC882469D9B39C000000004DC4DC9F2CB8EA660BB9BFFFC42082B2E39E415C05B9A08AFC6C6047BD43E00000026087BBAECD8");

        // Load sample TIM from JSON file
        JsonNode sampleTim = objectMapper.readTree(
                getClass().getResourceAsStream(
                        "/usdot/v2x/app/api/etx/configuration/SampleDecodedTim.json"));

        // Mock codec behavior to return the sample TIM as XML
        // Convert JSON to XML for the mock
        TravelerInformationMessageFrame messageFrame = objectMapper.readValue(sampleTim.toString(),
                TravelerInformationMessageFrame.class);
        String xer = xmlMapper.writeValueAsString(messageFrame);
        when(codec.uperToXer(any(byte[].class))).thenReturn(xer);

        // Create a simple geometry response
        GeometryFactory factory = new GeometryFactory();
        Coordinate[] coords = new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(1, 0),
                new Coordinate(1, 1),
                new Coordinate(0, 1),
                new Coordinate(0, 0)
        };
        Polygon jtsPolygon = factory.createPolygon(coords);
        TimCoordinateConverter.TimGeometry geometry = new TimCoordinateConverter.TimGeometry(
                factory.createLineString(coords), jtsPolygon);

        when(timConverter.convertTimToCoordinates(any())).thenReturn(geometry);

        // Mock getGeofences to return empty list
        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.just(List.of())) // For getGeofences call
                .thenReturn(Mono.just(new ConfigurationGeofenceResponse())); // For createGeofence call

        Mono<ResponseEntity<Void>> responseMono = configurationApi.deposit(request);

        StepVerifier.create(responseMono)
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
                    return true;
                })
                .verifyComplete();

        // Verify the geofence creation
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodySpec).bodyValue(bodyCaptor.capture());

        JsonNode actualJson = objectMapper.readTree(bodyCaptor.getValue());

        // Verify essential fields in the created geofence
        assertEquals("TIM_" + sampleTim.get("value").get("TravelerInformation").get("packetID").asText(),
                actualJson.get("name").asText());
        assertTrue(actualJson.get("isActive").asBoolean());
        assertEquals("j2735", actualJson.get("messages").get(0).get("generic").get("messageFormat").asText());
        assertEquals("TIM", actualJson.get("messages").get(0).get("generic").get("messageType").asText());
    }

    @Test
    public void testDeposit_TIM_NoDataFrames() throws Exception {
        DepositRequest request = new DepositRequest();
        request.setAsn1Hex("0123456789ABCDEF");

        // Mock TIM message frame without data frames
        TravelerInformationMessageFrame messageFrame = new TravelerInformationMessageFrame();
        TravelerInformation tim = new TravelerInformation();
        tim.setPacketID(new UniqueMSGID("8D442EF0020C6B1A09"));
        tim.setDataFrames(null);
        messageFrame.setValue(tim);

        String xer = xmlMapper.writeValueAsString(messageFrame);
        when(codec.uperToXer(any(byte[].class))).thenReturn(xer);

        Mono<ResponseEntity<Void>> responseMono = configurationApi.deposit(request);
        StepVerifier.create(responseMono)
                .expectError(ErrorResponseException.class)
                .verify();
    }

    @Test
    public void testDeposit_TIM_InvalidHex() throws Exception {
        DepositRequest request = new DepositRequest();
        request.setAsn1Hex("INVALID_HEX");

        Mono<ResponseEntity<Void>> responseMono = configurationApi.deposit(request);
        StepVerifier.create(responseMono)
                .expectError(ErrorResponseException.class)
                .verify();
    }

    @Test
    public void testDeposit_WithDeploymentRegion() throws Exception {
        DepositRequest request = new DepositRequest();
        request.setAsn1Hex(
                "001F647010F1E08D442EF0020C6B1A090F775D9B0301C26E26E4F965C75337FFF93F4878F07080A007F92A7BB7F741234E7D1B30EEC882469D9B39C000000004DC4DC9F2CB8EA660BB9BFFFC42082B2E39E415C05B9A08AFC6C6047BD43E00000026087BBAECD8");

        // Create a deployment region in GeoJSON format
        GeofenceFeatureCollection deploymentRegion = new GeofenceFeatureCollection();
        deploymentRegion.setType("FeatureCollection");

        GeofenceFeature feature = new GeofenceFeature();
        feature.setType("Feature");
        feature.setProperties(new HashMap<>());

        // Create a simple polygon geometry
        usdot.v2x.app.api.models.etx.configuration.geometry.Polygon polygon = new usdot.v2x.app.api.models.etx.configuration.geometry.Polygon();
        List<List<List<Double>>> coordinates = new ArrayList<>();
        List<List<Double>> ring = new ArrayList<>();
        ring.add(Arrays.asList(-122.4194, 37.7749)); // San Francisco coordinates
        ring.add(Arrays.asList(-122.4194, 37.7849));
        ring.add(Arrays.asList(-122.4094, 37.7849));
        ring.add(Arrays.asList(-122.4094, 37.7749));
        ring.add(Arrays.asList(-122.4194, 37.7749)); // Close the ring
        coordinates.add(ring);
        polygon.setCoordinates(coordinates);

        feature.setGeometry(polygon);
        deploymentRegion.setFeatures(Arrays.asList(feature));

        request.setOverrideGeofence(deploymentRegion);

        // Load sample TIM from JSON file
        JsonNode sampleTim = objectMapper.readTree(
                getClass().getResourceAsStream(
                        "/usdot/v2x/app/api/etx/configuration/SampleDecodedTim.json"));

        // Mock codec behavior to return the sample TIM as XML
        // Convert JSON to XML for the mock
        MessageFrame messageFrame = objectMapper.readValue(sampleTim.toString(), MessageFrame.class);
        String xer = xmlMapper.writeValueAsString(messageFrame);
        when(codec.uperToXer(any(byte[].class))).thenReturn(xer);

        // Mock getGeofences to return empty list
        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.just(List.of())) // For getGeofences call
                .thenReturn(Mono.just(new ConfigurationGeofenceResponse())); // For createGeofence call

        Mono<ResponseEntity<Void>> responseMono = configurationApi.deposit(request);

        StepVerifier.create(responseMono)
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
                    return true;
                })
                .verifyComplete();

        // Verify the geofence creation with deployment region
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodySpec).bodyValue(bodyCaptor.capture());

        JsonNode actualJson = objectMapper.readTree(bodyCaptor.getValue());

        // Verify essential fields in the created geofence
        assertEquals("TIM_" + sampleTim.get("value").get("TravelerInformation").get("packetID").asText(),
                actualJson.get("name").asText());
        assertTrue(actualJson.get("isActive").asBoolean());
        assertEquals("j2735", actualJson.get("messages").get(0).get("generic").get("messageFormat").asText());
        assertEquals("TIM", actualJson.get("messages").get(0).get("generic").get("messageType").asText());

        // Verify that the deployment region was used
        JsonNode geoFence = actualJson.get("geoFence");
        assertEquals("FeatureCollection", geoFence.get("type").asText());
        assertEquals(1, geoFence.get("features").size());
        assertEquals("Feature", geoFence.get("features").get(0).get("type").asText());
        assertEquals("Polygon", geoFence.get("features").get(0).get("geometry").get("type").asText());
    }

    @Test
    public void testClearGeofences_ClearTimOnly() throws Exception {
        // Create test geofences
        ConfigurationGeofenceSummary timGeofence = new ConfigurationGeofenceSummary();
        timGeofence.setId("tim-1");
        timGeofence.setName("TIM_123");
        timGeofence.setDescription(
                "001F647010F1E08D442EF0020C6B1A090F775D9B0301C26E26E4F965C75337FFF93F4878F07080A007F92A7BB7F741234E7D1B30EEC882469D9B39C000000004DC4DC9F2CB8EA660BB9BFFFC42082B2E39E415C05B9A08AFC6C6047BD43E00000026087BBAECD8");

        ConfigurationGeofenceSummary mapGeofence = new ConfigurationGeofenceSummary();
        mapGeofence.setId("map-1");
        mapGeofence.setName("MAP_456");

        // Mock the current time
        Instant fixedInstant = LocalDateTime.of(2025, 3, 6, 0, 0, 0)
                .toInstant(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(fixedInstant);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        // Mock getGeofences to return our test geofences
        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.just(List.of(timGeofence, mapGeofence))) // For getGeofences call
                .thenReturn(Mono.empty()); // For deleteGeofence call

        // Mock codec to return a TIM message that's not active (expired)
        TravelerInformationMessageFrame messageFrame = new TravelerInformationMessageFrame();
        TravelerInformation tim = new TravelerInformation();
        tim.setPacketID(new UniqueMSGID("8D442EF0020C6B1A09"));

        // Create data frame with expired time
        TravelerDataFrame dataFrame = new TravelerDataFrame();
        dataFrame.setStartYear(new DYear(2020L)); // Past year
        dataFrame.setStartTime(new MinuteOfTheYear(0L)); // Start of year
        dataFrame.setDurationTime(new MinutesDuration(60L)); // 1 hour duration
        TravelerDataFrameList dataFrames = new TravelerDataFrameList();
        dataFrames.add(dataFrame);
        tim.setDataFrames(dataFrames);

        messageFrame.setValue(tim);
        String xer = xmlMapper.writeValueAsString(messageFrame);
        when(codec.uperToXer(any(byte[].class))).thenReturn(xer);

        // Create request for TIM-only clearing
        ConfigurationClearGeofence request = new ConfigurationClearGeofence();
        request.setClearTimOnly(true);

        Mono<ResponseEntity<List<String>>> responseMono = configurationApi.clearGeofences(request);

        StepVerifier.create(responseMono)
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    return true;
                })
                .verifyComplete();

        // Verify that deleteGeofence was called only for the TIM geofence
        verify(webClient, times(1)).delete(); // Once for the TIM geofence
        ArgumentCaptor<Function<UriBuilder, URI>> uriFunctionCaptor = ArgumentCaptor.forClass(Function.class);
        verify(requestHeadersUriSpec).uri(uriFunctionCaptor.capture());
        Function<UriBuilder, URI> uriFunction = uriFunctionCaptor.getValue();
        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder();
        URI uri = uriFunction.apply(uriBuilder);
        assertTrue(uri.toString().contains("id=tim-1"), "Expected URI to contain tim-1");
    }

    @Test
    public void testClearGeofences_ClearAll() throws Exception {
        // Create test geofences
        ConfigurationGeofenceSummary timGeofence = new ConfigurationGeofenceSummary();
        timGeofence.setId("tim-1");
        timGeofence.setName("TIM_123");

        ConfigurationGeofenceSummary mapGeofence = new ConfigurationGeofenceSummary();
        mapGeofence.setId("map-1");
        mapGeofence.setName("MAP_456");

        // Mock getGeofences to return our test geofences
        when(requestHeadersSpec.exchangeToMono(any()))
                .thenReturn(Mono.just(List.of(timGeofence, mapGeofence))) // For getGeofences call
                .thenReturn(Mono.empty()) // For deleteGeofence calls
                .thenReturn(Mono.empty()); // For second deleteGeofence call

        // Create request for clearing all geofences
        ConfigurationClearGeofence request = new ConfigurationClearGeofence();
        request.setClearTimOnly(false);

        Mono<ResponseEntity<List<String>>> responseMono = configurationApi.clearGeofences(request);

        StepVerifier.create(responseMono)
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    return true;
                })
                .verifyComplete();

        // Verify that deleteGeofence was called for both geofences
        verify(webClient, times(2)).delete(); // Once for each geofence

        // Capture all URI function calls
        ArgumentCaptor<Function<UriBuilder, URI>> uriFunctionCaptor = ArgumentCaptor.forClass(Function.class);
        verify(requestHeadersUriSpec, times(2)).uri(uriFunctionCaptor.capture());

        // Get both captured values
        List<Function<UriBuilder, URI>> uriFunctions = uriFunctionCaptor.getAllValues();

        // Create URI builder for testing
        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder();

        // Verify both URIs were called
        List<String> actualUris = uriFunctions.stream()
                .map(fn -> fn.apply(uriBuilder).toString())
                .sorted()
                .toList();

        // Verify that we have exactly two URIs
        assertEquals(2, actualUris.size());

        // Verify that one URI is for tim-1
        assertTrue(actualUris.stream().anyMatch(uri -> uri.contains("id=tim-1")),
                "Expected to find URI containing tim-1");

        // Verify that one URI is for map-1
        assertTrue(actualUris.stream().anyMatch(uri -> uri.contains("id=map-1")),
                "Expected to find URI containing map-1");
    }

    @Test
    public void testIsTimMessageActive_Active() throws Exception {
        // Load sample TIM from JSON file
        JsonNode sampleTim = objectMapper.readTree(
                getClass().getResourceAsStream(
                        "/usdot/v2x/app/api/etx/configuration/SampleTim.json"));

        // Mock codec behavior to return the sample TIM as XML
        // Convert JSON to XML for the mock
        MessageFrame messageFrame = objectMapper.readValue(sampleTim.toString(), MessageFrame.class);
        String xer = xmlMapper.writeValueAsString(messageFrame);
        when(codec.uperToXer(any(byte[].class))).thenReturn(xer);

        // Mock the current time to be 2025-02-13T12:00:00Z
        // This TIM is active from 2025-02-13T00:00 to 2025-03-05T00:00
        Instant fixedInstant = LocalDateTime.of(2025, 2, 13, 12, 0, 0)
                .toInstant(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(fixedInstant);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        // Test with any hex string since we're mocking the codec
        boolean isActive = configurationApi.isTimMessageActive("0123456789ABCDEF");
        assertTrue(isActive, "TIM message should be active");
    }

    @Test
    public void testIsTimMessageActive_Inactive() throws Exception {
        // Load sample TIM from JSON file
        JsonNode sampleTim = objectMapper.readTree(
                getClass().getResourceAsStream(
                        "/usdot/v2x/app/api/etx/configuration/SampleTim.json"));

        // Mock codec behavior to return the sample TIM as XML
        // Convert JSON to XML for the mock
        MessageFrame messageFrame = objectMapper.readValue(sampleTim.toString(), MessageFrame.class);
        String xer = xmlMapper.writeValueAsString(messageFrame);
        when(codec.uperToXer(any(byte[].class))).thenReturn(xer);

        // Mock the current time to be 2025-03-06T00:00:00Z (after the TIM expires)
        Instant fixedInstant = LocalDateTime.of(2025, 3, 6, 0, 0, 0)
                .toInstant(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(fixedInstant);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        // Test with any hex string since we're mocking the codec
        boolean isActive = configurationApi.isTimMessageActive("0123456789ABCDEF");
        assertFalse(isActive, "TIM message should be inactive");
    }

    @Test
    public void testIsTimMessageActive_NoDataFrames() throws Exception {
        // create a TIM without data frames
        TravelerInformationMessageFrame messageFrame = new TravelerInformationMessageFrame();
        TravelerInformation tim = new TravelerInformation();
        tim.setPacketID(new UniqueMSGID("8D442EF0020C6B1A09"));
        tim.setDataFrames(null);
        messageFrame.setValue(tim);

        // Mock codec behavior to return the sample TIM
        String xer = xmlMapper.writeValueAsString(messageFrame);
        when(codec.uperToXer(any(byte[].class))).thenReturn(xer);

        // Test with any hex string since we're mocking the codec
        boolean isActive = configurationApi.isTimMessageActive("0123456789ABCDEF");
        assertFalse(isActive, "TIM message without data frames should be considered inactive");
    }

    @Test
    public void testIsTimMessageActive_NonTimMessage() throws Exception {
        // Load sample MAP message from JSON file
        JsonNode sampleMap = objectMapper.readTree(
                getClass().getResourceAsStream(
                        "/usdot/v2x/app/api/etx/configuration/SampleMap.json"));

        // Mock codec behavior to return the sample MAP as XML
        // Convert JSON to XML for the mock
        MessageFrame messageFrame = objectMapper.readValue(sampleMap.toString(), MessageFrame.class);
        String xer = xmlMapper.writeValueAsString(messageFrame);
        when(codec.uperToXer(any(byte[].class))).thenReturn(xer);

        // Test with any hex string since we're mocking the codec
        boolean isActive = configurationApi.isTimMessageActive("0123456789ABCDEF");
        assertTrue(isActive, "Non-TIM messages should be considered active");
    }
}