package usdot.v2x.app.api.etx.registration;

import usdot.v2x.app.api.config.etx.EtxProperties;
import usdot.v2x.app.api.etx.TokenService;
import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.etx.ErrorResponseException;
import usdot.v2x.app.api.models.etx.RegistrationResponsePendingException;
import usdot.v2x.app.api.models.etx.registration.*;
import usdot.v2x.app.api.utils.SecurityContextUtils;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * API client for ETX Registration API operations.
 */
@Slf4j
@Component
public class RegistrationApi {
    String etxVendorId;
    String etxDepositorVendorId;
    String etxUsername;
    String etxPassword;
    TokenService tokenService;
    private final WebClient webClient;
    private final SecurityContextUtils securityContextUtils;

    public RegistrationApi(
            EtxProperties etxProperties,
            TokenService tokenService,
            WebClient.Builder webClientBuilder,
            SecurityContextUtils securityContextUtils) {
        this.etxVendorId = etxProperties.getVendorId();
        this.etxDepositorVendorId = etxProperties.getDepositorVendorId();
        this.etxUsername = etxProperties.getUsername();
        this.etxPassword = etxProperties.getPassword();
        this.tokenService = tokenService;
        this.webClient = webClientBuilder.baseUrl(etxProperties.getEndpoint()).build();
        this.securityContextUtils = securityContextUtils;
    }

    public Mono<RegistrationResponse> clientRegistrationRetryPending(
            RegistrationPostRequest request,
            int numRetries,
            int numSecToSleep) {

        return clientRegistrationPost(request)
                .retryWhen(
                        Retry.fixedDelay(numRetries, Duration.ofSeconds(numSecToSleep))
                                .filter(throwable -> throwable instanceof RegistrationResponsePendingException));
    }

    public Mono<RegistrationResponse> clientRegistrationPutRetryPending(
            RegistrationPutRequest request,
            int numRetries,
            int numSecToSleep) {

        return clientRegistrationPut(request).retryWhen(
                Retry.fixedDelay(numRetries, Duration.ofSeconds(numSecToSleep))
                        .filter(throwable -> throwable instanceof RegistrationResponsePendingException));
    }

    public Mono<RegistrationResponse> clientRegistrationPost(RegistrationPostRequest request) {
        String vendorId = securityContextUtils.determineVendorId();
        RegistrationPostRequestWithVendorId requestBody = new RegistrationPostRequestWithVendorId(request, vendorId);
        log.debug("Registration request body: {}", requestBody);
        return tokenService.getTokenStore().flatMap(tokenStore -> webClient.post()
                .uri("/api/v2/clients/registration")
                .headers(headers -> {
                    headers.set("Authorization", "Bearer " + tokenStore.getAccessToken());
                    headers.set("SessionToken", tokenStore.getSessionToken());
                    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                })
                .bodyValue(requestBody)
                .exchangeToMono(response -> switch (response.statusCode()) {
                    case HttpStatus.OK -> response.bodyToMono(RegistrationResponse.class);
                    case HttpStatus.ACCEPTED -> response.bodyToMono(RegistrationPendingResponse.class)
                            .flatMap(errorResponse -> Mono
                                    .error(new RegistrationResponsePendingException(errorResponse,
                                            response.statusCode())));
                    default -> {
                        log.warn("Received non-success error code: {}", response.statusCode());
                        yield response.bodyToMono(ErrorResponse.class)
                                .flatMap(errorResponse -> Mono
                                        .error(new ErrorResponseException(errorResponse, response.statusCode())));
                    }
                }));
    }

    public Mono<RegistrationResponse> clientRegistrationPut(RegistrationPutRequest request) {
        String vendorId = securityContextUtils.determineVendorId();

        return tokenService.getTokenStore().flatMap(tokenStore -> webClient.put()
                .uri("/api/v2/clients/registration")
                .headers(headers -> {
                    headers.set("Authorization", "Bearer " + tokenStore.getAccessToken());
                    headers.set("SessionToken", tokenStore.getSessionToken());
                    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                    headers.set("DeviceID", request.getDeviceId());
                    headers.set("VendorID", vendorId);
                })
                .exchangeToMono(response -> switch (response.statusCode()) {
                    case HttpStatus.OK -> response.bodyToMono(RegistrationResponse.class);
                    case HttpStatus.ACCEPTED -> response.bodyToMono(RegistrationPendingResponse.class)
                            .flatMap(errorResponse -> Mono
                                    .error(new RegistrationResponsePendingException(errorResponse,
                                            response.statusCode())));
                    default -> response.bodyToMono(ErrorResponse.class)
                            .flatMap(errorResponse -> Mono.error(new ErrorResponseException(errorResponse,
                                    response.statusCode())));
                }));
    }

    public Mono<ConnectionResponse> clientConnectionPost(ConnectionPostRequest request) {
        String vendorId = securityContextUtils.determineVendorId();

        return tokenService.getTokenStore().flatMap(tokenStore -> webClient.post()
                .uri("/api/v2/clients/connection")
                .headers(headers -> {
                    headers.set("Authorization", "Bearer " + tokenStore.getAccessToken());
                    headers.set("SessionToken", tokenStore.getSessionToken());
                    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                    headers.set("VendorID", vendorId);
                })
                .bodyValue(new ConnectionPostRequestEtx(request))
                .exchangeToMono(response -> switch (response.statusCode()) {
                    case HttpStatus.OK -> response.bodyToMono(ConnectionResponse.class);
                    default -> response.bodyToMono(ErrorResponse.class)
                            .flatMap(errorResponse -> Mono.error(new ErrorResponseException(errorResponse,
                                    response.statusCode())));
                }));
    }

    /**
     * Delete registrations by device IDs with specific vendor ID
     */
    public Mono<Void> deleteRegistrations(List<String> deviceIds, String vendorId) {
        return tokenService.getTokenStore().flatMap(tokenStore -> {
            // Try passing device IDs as individual DeviceIDs parameters
            // This matches the pattern: ?DeviceIDs=id1&DeviceIDs=id2
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/api/v2/clients/registration");
            for (String deviceId : deviceIds) {
                uriBuilder.queryParam("DeviceIDs", deviceId);
            }
            String uri = uriBuilder.build().toUriString();

            return webClient.delete()
                    .uri(uri)
                    .headers(headers -> {
                        headers.set("Authorization", "Bearer " + tokenStore.getAccessToken());
                        headers.set("SessionToken", tokenStore.getSessionToken());
                        headers.set("VendorID", vendorId);
                    })
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            log.info("Successfully deleted {} registrations for vendor {}", deviceIds.size(), vendorId);
                            return Mono.empty();
                        } else {
                            log.warn("Failed to delete registrations for vendor {}, status: {}", vendorId,
                                    response.statusCode());
                            // Log the response body for debugging
                            return response.bodyToMono(String.class)
                                    .doOnNext(body -> log.error("ETX API error response body: {}", body))
                                    .then(response.bodyToMono(ErrorResponse.class)
                                            .flatMap(errorResponse -> Mono
                                                    .error(new ErrorResponseException(errorResponse,
                                                            response.statusCode()))));
                        }
                    });
        });
    }

    /**
     * Check registration status by device ID
     */
    public Mono<RegistrationCheckResponse> checkRegistration(String deviceId, String vendorId) {
        return tokenService.getTokenStore().flatMap(tokenStore -> {
            String uri = UriComponentsBuilder.fromPath("/api/v2/clients/registration")
                    .queryParam("DeviceID", deviceId)
                    .build()
                    .toUriString();

            log.debug("Check registration URI: {}", uri);
            log.debug("Device ID: {}", deviceId);

            return webClient.get()
                    .uri(uri)
                    .headers(headers -> {
                        headers.set("Authorization", "Bearer " + tokenStore.getAccessToken());
                        headers.set("SessionToken", tokenStore.getSessionToken());
                        headers.set("VendorID", vendorId);
                        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                    })
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            log.info("Successfully checked registration for device {} and vendor {}", deviceId,
                                    vendorId);
                            return response.bodyToMono(RegistrationCheckResponse.class)
                                    .flatMap(registrationCheckResponse -> {
                                        // Verify that the VendorID in the response matches the requesting vendor
                                        String responseVendorId = registrationCheckResponse.getVendorId();
                                        if (responseVendorId == null || !responseVendorId.equals(vendorId)) {
                                            log.warn(
                                                    "VendorID mismatch for device {}. Requested vendor: {}, Response vendor: {}",
                                                    deviceId, vendorId, responseVendorId);
                                            ErrorResponse errorResponse = new ErrorResponse(
                                                    "Device not found",
                                                    String.format("Device %s is not registered for vendor %s", deviceId,
                                                            vendorId));
                                            return Mono.error(
                                                    new ErrorResponseException(errorResponse, HttpStatus.NOT_FOUND));
                                        }
                                        return Mono.just(registrationCheckResponse);
                                    });
                        } else {
                            log.warn("Failed to check registration for device {} and vendor {}, status: {}", deviceId,
                                    vendorId,
                                    response.statusCode());
                            // Return the error response directly to the user
                            return response.bodyToMono(ErrorResponse.class)
                                    .doOnNext(errorResponse -> log.error("ETX API error response: {}", errorResponse))
                                    .flatMap(errorResponse -> Mono
                                            .error(new ErrorResponseException(errorResponse,
                                                    response.statusCode())));
                        }
                    });
        });
    }

    /**
     * Get device roles/ACLs for a vendor from Thingspace
     */
    public Mono<Object> getDeviceRoles() {
        // Determine vendor ID before making reactive calls
        String vendorId = securityContextUtils.determineVendorId();

        return tokenService.getTokenStore().flatMap(tokenStore -> {
            String uri = UriComponentsBuilder.fromPath("/api/v1/device-roles/vendor")
                    .queryParam("VendorID", vendorId)
                    .build()
                    .toUriString();

            log.debug("Get device roles URI: {}", uri);
            log.debug("Vendor ID: {}", vendorId);

            return webClient.get()
                    .uri(uri)
                    .headers(headers -> {
                        headers.set("Authorization", "Bearer " + tokenStore.getAccessToken());
                        headers.set("SessionToken", tokenStore.getSessionToken());
                        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                    })
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            log.info("Successfully retrieved device roles for vendor {}", vendorId);
                            return response.bodyToMono(Object.class);
                        } else {
                            log.warn("Failed to retrieve device roles for vendor {}, status: {}", vendorId,
                                    response.statusCode());
                            return response.bodyToMono(ErrorResponse.class)
                                    .doOnNext(errorResponse -> log.error("Thingspace API error response: {}",
                                            errorResponse))
                                    .flatMap(errorResponse -> Mono
                                            .error(new ErrorResponseException(errorResponse,
                                                    response.statusCode())));
                        }
                    });
        });
    }
}
