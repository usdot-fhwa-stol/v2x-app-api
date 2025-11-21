package usdot.v2x.app.api.etx.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import usdot.v2x.app.api.config.etx.EtxProperties;
import usdot.v2x.app.api.etx.TokenService;
import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.etx.ErrorResponseException;
import usdot.v2x.app.api.models.etx.configuration.ConfigurationGeofenceResponse;
import usdot.v2x.app.api.models.etx.configuration.DepositRequest;
import usdot.v2x.app.api.models.etx.configuration.ConfigurationGeofence;
import usdot.v2x.app.api.models.etx.configuration.ConfigurationGeofenceSummary;
import usdot.v2x.app.api.models.etx.configuration.ConfigurationClearGeofence;
import usdot.v2x.app.api.models.etx.configuration.geofence.DistributionSchedule;
import usdot.v2x.app.api.models.etx.configuration.geofence.DistributionType;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;
import usdot.v2x.app.api.models.etx.configuration.geofence.RoadUserType;
import usdot.v2x.app.api.models.etx.configuration.geofence.TriggerCondition;
import usdot.v2x.app.api.models.etx.configuration.messages.GenericMessage;
import usdot.v2x.app.api.models.etx.configuration.messages.GenericMessageItem;
import usdot.v2x.app.api.utils.UperUtil;
import usdot.v2x.app.api.utils.TimCoordinateConverter;
import usdot.v2x.app.api.utils.MapCoordinateConverter;
import usdot.v2x.app.api.utils.GeofenceFeatureHelper;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeature;
import org.locationtech.jts.geom.Polygon;

/**
 * API client for ETX Configuration API operations.
 */
import java.util.HashMap;

import j2735ffm.MessageFrameCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import us.dot.its.jpo.asn.j2735.r2024.MapData.MapData;
import us.dot.its.jpo.asn.j2735.r2024.MapData.MapDataMessageFrame;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

@Slf4j
@Component
@ConditionalOnBean(MessageFrameCodec.class)
public class ConfigurationApi {

    private String etxVendorId;
    private TokenService tokenService;
    private WebClient webClient;
    private MessageFrameCodec codec;
    private ObjectMapper jsonMapper;
    private XmlMapper xmlMapper;
    private Clock clock;
    private DistributionType distributionType;
    private TimCoordinateConverter timCoordinateConverter;
    private MapCoordinateConverter mapCoordinateConverter;

    // For testing to mock the current time
    @Autowired
    public ConfigurationApi(
            EtxProperties etxProperties,
            TokenService tokenService,
            WebClient.Builder webClientBuilder,
            MessageFrameCodec codec,
            ObjectMapper jsonMapper,
            @Qualifier("xmlMapper") XmlMapper xmlMapper) {
        this(etxProperties, tokenService, webClientBuilder, codec, jsonMapper, xmlMapper, Clock.systemUTC(), null,
                null);
    }

    public ConfigurationApi(
            EtxProperties etxProperties,
            TokenService tokenService,
            WebClient.Builder webClientBuilder,
            MessageFrameCodec codec,
            ObjectMapper jsonMapper,
            XmlMapper xmlMapper,
            Clock clock) {
        this(etxProperties, tokenService, webClientBuilder, codec, jsonMapper, xmlMapper, clock, null, null);
    }

    public ConfigurationApi(
            EtxProperties etxProperties,
            TokenService tokenService,
            WebClient.Builder webClientBuilder,
            MessageFrameCodec codec,
            ObjectMapper jsonMapper,
            XmlMapper xmlMapper,
            Clock clock,
            TimCoordinateConverter timCoordinateConverter,
            MapCoordinateConverter mapCoordinateConverter) {
        this.etxVendorId = etxProperties.getVendorId();
        this.tokenService = tokenService;
        this.webClient = webClientBuilder.baseUrl(etxProperties.getEndpoint() + "/api/v1/application/configurations")
                .build();
        this.jsonMapper = jsonMapper;
        this.xmlMapper = xmlMapper;
        this.codec = codec;
        this.clock = clock;
        this.distributionType = etxProperties.getConfiguration().getDistributionType();
        this.timCoordinateConverter = timCoordinateConverter;
        this.mapCoordinateConverter = mapCoordinateConverter;
    }

    /**
     * Sets up common HTTP headers for API requests
     */
    private void setupCommonHeaders(org.springframework.http.HttpHeaders headers, String accessToken,
            String sessionToken) {
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("SessionToken", sessionToken);
        headers.set("VendorID", etxVendorId);
        headers.set("Content-Type", "application/json");
        headers.set("Accept", "application/json");
    }

    /**
     * Parses ASN1 hex string to MessageFrame
     */
    private MessageFrame<?> parseMessageFrame(String asn1Hex) throws JsonProcessingException {
        try {
            // Trim the hex string to remove any headers and get just the message payload
            String trimmedHex = UperUtil.trimToMessagePayload(asn1Hex);
            byte[] bytes = HexFormat.of().parseHex(trimmedHex);
            String xer = codec.uperToXer(bytes);
            return xmlMapper.readValue(xer, MessageFrame.class);
        } catch (Exception e) {
            log.error("Failed to parse ASN1 hex", e);
            throw new ErrorResponseException(
                    new ErrorResponse("Failed to parse ASN1 hex", e.getMessage()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * Extracts message information from TIM message frame
     */
    private MessageInfo extractTimMessageInfo(TravelerInformationMessageFrame messageFrame) {
        TravelerInformation tim = messageFrame.getValue();
        String packetIdHex = tim.getPacketID().getValue();
        String name = "TIM_" + packetIdHex;

        List<TravelerDataFrame> dataFrames = tim.getDataFrames();
        if (dataFrames == null || dataFrames.isEmpty()) {
            throw new ErrorResponseException(
                    new ErrorResponse("TIM message must contain at least one data frame", "invalid TIM message"),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        TravelerDataFrame firstFrame = dataFrames.getFirst();
        return new MessageInfo(
                name,
                firstFrame.getStartYear().getValue(),
                firstFrame.getStartTime().getValue(),
                firstFrame.getDurationTime().getValue(),
                "TIM");
    }

    /**
     * Extracts message information from MAP message frame
     */
    private MessageInfo extractMapMessageInfo(MapDataMessageFrame messageFrame) {
        MapData mapData = messageFrame.getValue();
        String packetIdHex = String.valueOf(mapData.getIntersections().getFirst().getId().getId().getValue());
        String name = "MAP_" + packetIdHex;

        return new MessageInfo(
                name,
                LocalDateTime.now().getYear(),
                LocalDateTime.now().getMinute(),
                32000, // Maximum duration for broadcast
                "MAP");
    }

    /**
     * Creates a GenericMessage with common settings
     */
    private GenericMessage createGenericMessage(DepositRequest request, String messageType,
            String base64Asn1, long startYear, long startTimeMinutes, long durationTime) {
        GenericMessage message = new GenericMessage();

        if (distributionType == null || distributionType == DistributionType.Targeted) {
            message.setDistributionType(Arrays.asList(DistributionType.Targeted));
        } else {
            message.setDistributionType(Arrays.asList(DistributionType.Broadcast));
            DistributionSchedule schedule = new DistributionSchedule();
            schedule.setRepeatPeriod(5);
            schedule.setDuration(Integer.parseInt(String.valueOf(durationTime)));
            schedule.setStartTime(getIsoTime((int) startYear, (int) startTimeMinutes));
            message.setDistributionSchedule(schedule);
        }

        message.setRoadUserType(Arrays.asList(RoadUserType.Vehicle, RoadUserType.VulnerableRoadUser));
        message.setTriggerConditions(List.of(TriggerCondition.inside));
        message.setPrivate(false);
        message.setDistributionSchedule(null);

        GenericMessageItem generic = new GenericMessageItem();
        generic.setMessageType(messageType);
        generic.setMessageFormat("j2735");
        generic.setPayload(base64Asn1);
        message.setGeneric(generic);

        return message;
    }

    private String getIsoTime(int year, int minutesIntoYear) {
        LocalDateTime startOfYear = LocalDateTime.of(year, 1, 1, 0, 0);
        LocalDateTime timestamp = startOfYear.plusMinutes(minutesIntoYear);
        // Format with seconds explicitly set to 00
        return timestamp.atOffset(ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
    }

    /**
     * Creates a ConfigurationGeofence with common settings
     */
    private ConfigurationGeofence createConfigurationGeofence(String name, String description,
            GeofenceFeatureCollection geoFence, GenericMessage message) {
        ConfigurationGeofence geofence = new ConfigurationGeofence();
        geofence.setName(name);
        geofence.setDescription(description);
        geofence.setGeoFence(geoFence);
        geofence.setMessages(List.of(message));
        geofence.setActive(true);
        return geofence;
    }

    /**
     * Converts hex ASN1 to base64
     */
    private String hexToBase64(String asn1Hex) {
        byte[] hexBytes = HexFormat.of().parseHex(asn1Hex);
        return Base64.getEncoder().encodeToString(hexBytes);
    }

    /**
     * Creates or updates a geofence based on whether it already exists
     */
    private void createOrUpdateGeofenceIfExists(String name, ConfigurationGeofence geofence) {
        List<ConfigurationGeofenceSummary> existingGeofences = getGeofences().block();
        ConfigurationGeofenceSummary existing = existingGeofences.stream()
                .filter(g -> g.getName().equals(name))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            updateGeofence(existing.getId(), geofence).block();
        } else {
            createGeofence(geofence).block();
        }
    }

    public Mono<List<ConfigurationGeofenceSummary>> getGeofences() {
        return tokenService.getTokenStore().flatMap(tokenStore -> webClient.get()
                .uri("/geofence/ids")
                .headers(headers -> {
                    headers.set("Authorization", "Bearer " + tokenStore.getAccessToken());
                    headers.set("SessionToken", tokenStore.getSessionToken());
                    headers.set("VendorID", etxVendorId);
                    headers.set("Accept", "application/json");
                })
                .exchangeToMono(response -> switch (response.statusCode()) {
                    case HttpStatus.OK ->
                        response.bodyToMono(new ParameterizedTypeReference<List<ConfigurationGeofenceSummary>>() {
                        });
                    default -> response.bodyToMono(ErrorResponse.class)
                            .flatMap(errorResponse -> Mono.error(new ErrorResponseException(errorResponse,
                                    response.statusCode())));
                })
                .retryWhen(usdot.v2x.app.api.config.WebClientConfig.getRetrySpec())
                .doOnError(error -> log.error("Failed to get geofences after retries: {}", error.getMessage())));
    }

    public Mono<ConfigurationGeofenceResponse> getGeofence(String id) {
        return tokenService.getTokenStore().flatMap(tokenStore -> webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/geofence").queryParam("id", id).build())
                .headers(headers -> setupCommonHeaders(headers, tokenStore.getAccessToken(),
                        tokenStore.getSessionToken()))
                .exchangeToMono(response -> switch (response.statusCode()) {
                    case HttpStatus.OK -> response.bodyToMono(ConfigurationGeofenceResponse.class);
                    default -> response.bodyToMono(ErrorResponse.class)
                            .flatMap(errorResponse -> Mono.error(new ErrorResponseException(errorResponse,
                                    response.statusCode())));
                }));
    }

    public Mono<ConfigurationGeofenceResponse> createGeofence(ConfigurationGeofence geofence) {
        return tokenService.getTokenStore().flatMap(tokenStore -> {
            try {
                log.debug("createGeofence geofence");
                log.debug(jsonMapper.writeValueAsString(geofence));
                return webClient.post()
                        .uri("/geofence")
                        .headers(headers -> setupCommonHeaders(headers, tokenStore.getAccessToken(),
                                tokenStore.getSessionToken()))
                        .bodyValue(jsonMapper.writeValueAsString(geofence))
                        .exchangeToMono(response -> switch (response.statusCode()) {
                            case HttpStatus.CREATED -> response.bodyToMono(ConfigurationGeofenceResponse.class);
                            default -> response.bodyToMono(ErrorResponse.class)
                                    .flatMap(errorResponse -> Mono.error(new ErrorResponseException(errorResponse,
                                            response.statusCode())));
                        });
            } catch (JsonProcessingException e) {
                return Mono.error(new ErrorResponseException(
                        new ErrorResponse("Failed to create geofence", "failed to create geofence"),
                        HttpStatus.INTERNAL_SERVER_ERROR));
            }
        });
    }

    public Mono<ConfigurationGeofenceResponse> updateGeofence(String id, ConfigurationGeofence geofence) {
        return tokenService.getTokenStore().flatMap(tokenStore -> {
            try {
                log.debug("updateGeofence geofence");
                log.debug(jsonMapper.writeValueAsString(geofence));
                return webClient.put()
                        .uri(uriBuilder -> uriBuilder.path("/geofence").queryParam("id", id).build())
                        .headers(headers -> setupCommonHeaders(headers, tokenStore.getAccessToken(),
                                tokenStore.getSessionToken()))
                        .bodyValue(jsonMapper.writeValueAsString(geofence))
                        .exchangeToMono(response -> switch (response.statusCode()) {
                            case HttpStatus.CREATED -> response.bodyToMono(ConfigurationGeofenceResponse.class);
                            default -> response.bodyToMono(ErrorResponse.class)
                                    .flatMap(errorResponse -> Mono.error(new ErrorResponseException(errorResponse,
                                            response.statusCode())));
                        });
            } catch (JsonProcessingException e) {
                return Mono.error(new ErrorResponseException(
                        new ErrorResponse("Failed to update geofence", "failed to update geofence"),
                        HttpStatus.INTERNAL_SERVER_ERROR));
            }
        });
    }

    public Mono<Void> deleteGeofence(String id) {
        return tokenService.getTokenStore().flatMap(tokenStore -> webClient.delete()
                .uri(uriBuilder -> uriBuilder.path("/geofence").queryParam("id", id).build())
                .headers(headers -> setupCommonHeaders(headers, tokenStore.getAccessToken(),
                        tokenStore.getSessionToken()))
                .exchangeToMono(response -> switch (response.statusCode()) {
                    case HttpStatus.NO_CONTENT -> Mono.empty();
                    default -> response.bodyToMono(ErrorResponse.class)
                            .flatMap(errorResponse -> Mono.error(new ErrorResponseException(errorResponse,
                                    response.statusCode())));
                }));
    }

    public ResponseEntity<Void> deposit(DepositRequest request) throws JsonProcessingException {
        MessageFrame<?> messageFrame = parseMessageFrame(request.getAsn1Hex());

        // Extract geofence from message if override geofence is not provided
        GeofenceFeatureCollection deploymentRegion = request.getOverrideGeofence();
        if (deploymentRegion == null) {
            deploymentRegion = extractGeofenceFromMessage(messageFrame);
        }

        return createOrUpdateGeofenceWithDeploymentRegion(request, messageFrame, deploymentRegion);
    }

    private ResponseEntity<Void> createOrUpdateGeofenceWithDeploymentRegion(
            DepositRequest request,
            MessageFrame<?> messageFrame,
            GeofenceFeatureCollection deploymentRegion) throws JsonProcessingException {

        MessageInfo messageInfo;
        if (messageFrame instanceof TravelerInformationMessageFrame) {
            messageInfo = extractTimMessageInfo((TravelerInformationMessageFrame) messageFrame);
        } else if (messageFrame instanceof MapDataMessageFrame) {
            messageInfo = extractMapMessageInfo((MapDataMessageFrame) messageFrame);
        } else {
            throw new ErrorResponseException(
                    new ErrorResponse("Invalid message type", "invalid message type"),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        return createOrUpdateGeofence(request, messageInfo.name, deploymentRegion,
                messageInfo.startYear, messageInfo.startTimeMinutes, messageInfo.durationTime, messageInfo.messageType);
    }

    private ResponseEntity<Void> createOrUpdateGeofence(
            DepositRequest request,
            String name,
            GeofenceFeatureCollection deploymentRegion,
            long startYear,
            long startTimeMinutes,
            long durationTime,
            String messageType) throws JsonProcessingException {

        String base64Asn1 = hexToBase64(request.getAsn1Hex());
        GenericMessage message = createGenericMessage(request, messageType, base64Asn1, startYear, startTimeMinutes,
                durationTime);
        ConfigurationGeofence geofence = createConfigurationGeofence(name, request.getAsn1Hex(), deploymentRegion,
                message);

        createOrUpdateGeofenceIfExists(name, geofence);
        return ResponseEntity.noContent().build();
    }

    public ResponseEntity<List<String>> clearGeofences(ConfigurationClearGeofence config)
            throws JsonProcessingException {
        List<ConfigurationGeofenceSummary> geofences = getGeofences().block();
        if (geofences == null) {
            throw new ErrorResponseException(
                    new ErrorResponse("Failed to get geofences list", "failed to get geofences list"),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        List<String> clearedIds;
        if (config.isClearTimOnly()) {
            log.debug("Clearing active TIM geofences");
            clearedIds = clearActiveTimGeofences(geofences);
        } else {
            log.debug("Clearing all geofences");
            clearedIds = clearAllGeofences(geofences);
        }

        return ResponseEntity.ok(clearedIds);
    }

    private List<String> clearAllGeofences(List<ConfigurationGeofenceSummary> geofences) {
        return Flux.fromIterable(geofences)
                .parallel()
                .flatMap(geofence -> deleteGeofence(geofence.getId())
                        .thenReturn(geofence.getId())
                        .onErrorResume(e -> {
                            log.error("Failed to delete geofence {}: {}", geofence.getId(), e.getMessage());
                            return Mono.empty();
                        }))
                .sequential()
                .collectList()
                .block();
    }

    private List<String> clearActiveTimGeofences(List<ConfigurationGeofenceSummary> geofences)
            throws JsonProcessingException {
        return Flux.fromIterable(geofences)
                .filter(geofence -> geofence.getName().contains("TIM"))
                .flatMap(geofence -> {
                    try {
                        String hexAsn1 = geofence.getDescription();
                        if (!isTimMessageActive(hexAsn1)) {
                            log.trace("Deleting geofence {}", geofence.getId());
                            return deleteGeofence(geofence.getId())
                                    .thenReturn(geofence.getId())
                                    .onErrorResume(e -> {
                                        log.error("Failed to delete geofence {}: {}", geofence.getId(), e.getMessage());
                                        return Mono.empty();
                                    });
                        }
                    } catch (Exception e) {
                        log.error("Failed to process geofence {}: {}", geofence.getId(), e.getMessage());
                    }
                    return Mono.empty();
                })
                .collectList()
                .block();
    }

    protected boolean isTimMessageActive(String asn1) throws JsonProcessingException {
        log.trace("Getting timestamp for ASN1: {}", asn1);
        MessageFrame<?> messageFrame = parseMessageFrame(asn1);

        if (messageFrame instanceof TravelerInformationMessageFrame) {
            TravelerInformation tim = ((TravelerInformationMessageFrame) messageFrame).getValue();
            List<TravelerDataFrame> dataFrames = tim.getDataFrames();

            if (dataFrames == null || dataFrames.isEmpty()) {
                log.error("No data frames found in TIM message");
                return false; // Assume inactive if we can't determine
            }

            TravelerDataFrame dataFrame = dataFrames.getFirst();
            long startYear = dataFrame.getStartYear().getValue();
            long startTimeMinutes = dataFrame.getStartTime().getValue();
            long durationTime = dataFrame.getDurationTime().getValue();

            LocalDateTime startOfYear = LocalDateTime.of((int) startYear, 1, 1, 0, 0);
            LocalDateTime startTime = startOfYear.plusMinutes(startTimeMinutes);
            LocalDateTime endTime = startTime.plusMinutes(durationTime);
            LocalDateTime currentTime = LocalDateTime.now(clock);

            return endTime.isAfter(currentTime);
        } else {
            log.error("Invalid message frame type: {}", messageFrame.getClass().getName());
            return true;
        }
    }

    /**
     * Deletes geofences by identifier (TIM packet ID or intersection ID)
     */
    public ResponseEntity<List<String>> deleteGeofencesByIdentifier(String identifier) {
        List<ConfigurationGeofenceSummary> geofences = getGeofences().block();
        if (geofences == null) {
            throw new ErrorResponseException(
                    new ErrorResponse("Failed to get geofences list", "failed to get geofences list"),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // Try to match as TIM packet ID first
        String timGeofenceName = "TIM_" + identifier;
        List<String> deletedIds = Flux.fromIterable(geofences)
                .filter(geofence -> geofence.getName().equals(timGeofenceName))
                .flatMap(geofence -> deleteGeofence(geofence.getId())
                        .thenReturn(geofence.getId())
                        .onErrorResume(e -> {
                            log.error("Failed to delete geofence {}: {}", geofence.getId(), e.getMessage());
                            return Mono.empty();
                        }))
                .collectList()
                .block();

        // If no TIM geofences found, try to match as intersection ID
        if (deletedIds.isEmpty()) {
            String mapGeofenceName = "MAP_" + identifier;
            deletedIds = Flux.fromIterable(geofences)
                    .filter(geofence -> geofence.getName().equals(mapGeofenceName))
                    .flatMap(geofence -> deleteGeofence(geofence.getId())
                            .thenReturn(geofence.getId())
                            .onErrorResume(e -> {
                                log.error("Failed to delete geofence {}: {}", geofence.getId(), e.getMessage());
                                return Mono.empty();
                            }))
                    .collectList()
                    .block();
        }

        if (deletedIds.isEmpty()) {
            throw new ErrorResponseException(
                    new ErrorResponse("No geofences found with identifier: " + identifier
                            + " (tried as TIM packet ID and intersection ID)", "geofence not found"),
                    HttpStatus.NOT_FOUND);
        }

        return ResponseEntity.ok(deletedIds);
    }

    /**
     * Extract geofence from ASN.1 message frame
     * 
     * @param messageFrame The parsed message frame
     * @return GeofenceFeatureCollection extracted from the message
     */
    private GeofenceFeatureCollection extractGeofenceFromMessage(MessageFrame<?> messageFrame) {
        if (messageFrame instanceof TravelerInformationMessageFrame) {
            return extractGeofenceFromTim((TravelerInformationMessageFrame) messageFrame);
        } else if (messageFrame instanceof MapDataMessageFrame) {
            return extractGeofenceFromMap((MapDataMessageFrame) messageFrame);
        } else {
            throw new ErrorResponseException(
                    new ErrorResponse("UNSUPPORTED_MESSAGE_TYPE",
                            "Message type " + messageFrame.getClass().getSimpleName()
                                    + " is not supported. Only TIM and MAP messages are currently supported."),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * Extract geofence from TIM message
     * 
     * @param timFrame The TIM message frame
     * @return GeofenceFeatureCollection extracted from the TIM message
     */
    private GeofenceFeatureCollection extractGeofenceFromTim(TravelerInformationMessageFrame timFrame) {
        if (timCoordinateConverter == null) {
            throw new ErrorResponseException(
                    new ErrorResponse("OVERRIDE_GEOFENCE_REQUIRED",
                            "Override geofence is required when TimCoordinateConverter is not available. Cannot extract geofence from TIM message."),
                    HttpStatus.BAD_REQUEST);
        }

        try {
            TravelerInformation tim = timFrame.getValue();
            List<TravelerDataFrame> dataFrames = tim.getDataFrames();
            if (dataFrames == null || dataFrames.isEmpty()) {
                throw new ErrorResponseException(
                        new ErrorResponse("TIM message must contain at least one data frame", "invalid TIM message"),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            TimCoordinateConverter.TimGeometry geometry = timCoordinateConverter.convertTimToCoordinates(dataFrames);
            return convertPolygonToGeofenceFeatureCollection(geometry.geofenceGeometry);
        } catch (ErrorResponseException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error extracting geofence from TIM message: {}", e.getMessage(), e);
            throw new ErrorResponseException(
                    new ErrorResponse("FAILED_TO_EXTRACT_GEOFENCE",
                            "Failed to extract geofence from TIM message: " + e.getMessage()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * Extract geofence from MAP message
     * 
     * @param mapFrame The MAP message frame
     * @return GeofenceFeatureCollection extracted from the MAP message
     */
    private GeofenceFeatureCollection extractGeofenceFromMap(MapDataMessageFrame mapFrame) {
        if (mapCoordinateConverter == null) {
            throw new ErrorResponseException(
                    new ErrorResponse("OVERRIDE_GEOFENCE_REQUIRED",
                            "Override geofence is required when MapCoordinateConverter is not available. Cannot extract geofence from MAP message."),
                    HttpStatus.BAD_REQUEST);
        }

        try {
            MapData mapData = mapFrame.getValue();
            us.dot.its.jpo.asn.j2735.r2024.MapData.IntersectionGeometryList intersections = mapData.getIntersections();
            if (intersections == null || intersections.isEmpty()) {
                throw new ErrorResponseException(
                        new ErrorResponse("MAP message must contain at least one intersection", "invalid MAP message"),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            MapCoordinateConverter.MapGeometry geometry = mapCoordinateConverter.convertMapToCoordinates(intersections);
            return convertPolygonToGeofenceFeatureCollection(geometry.geofenceGeometry);
        } catch (ErrorResponseException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error extracting geofence from MAP message: {}", e.getMessage(), e);
            throw new ErrorResponseException(
                    new ErrorResponse("FAILED_TO_EXTRACT_GEOFENCE",
                            "Failed to extract geofence from MAP message: " + e.getMessage()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * Convert JTS Polygon to GeofenceFeatureCollection
     * 
     * @param polygon The JTS Polygon to convert
     * @return GeofenceFeatureCollection containing the polygon
     */
    private GeofenceFeatureCollection convertPolygonToGeofenceFeatureCollection(Polygon polygon) {
        GeofenceFeature feature = new GeofenceFeature();
        feature.setType("Feature");
        feature.setProperties(new HashMap<>());

        // Use the helper to convert JTS Polygon to custom geometry
        GeofenceFeatureHelper.setGeometry(feature, polygon);

        GeofenceFeatureCollection geoFence = new GeofenceFeatureCollection();
        geoFence.setType("FeatureCollection");
        geoFence.setFeatures(List.of(feature));

        return geoFence;
    }

    /**
     * Helper class to hold message information extracted from message frames
     */
    private static class MessageInfo {
        final String name;
        final long startYear;
        final long startTimeMinutes;
        final long durationTime;
        final String messageType;

        MessageInfo(String name, long startYear, long startTimeMinutes, long durationTime, String messageType) {
            this.name = name;
            this.startYear = startYear;
            this.startTimeMinutes = startTimeMinutes;
            this.durationTime = durationTime;
            this.messageType = messageType;
        }
    }
}
