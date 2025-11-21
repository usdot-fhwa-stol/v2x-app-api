package usdot.v2x.app.api.keycloak;

import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.keycloak.KeycloakErrorResponse;
import usdot.v2x.app.api.models.keycloak.TokenPostRequest;
import usdot.v2x.app.api.models.keycloak.TokenPostResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * REST controller for Keycloak authentication operations.
 */
@Slf4j
@RestController
@RequestMapping("auth")
@Tag(name = "Authentication", description = "Authentication and authorization for Registration and Configuration endpoints")
public class KeycloakRestController {
    KeycloakApi keycloakApi;

    KeycloakRestController(
            KeycloakApi keycloakApi) {
        this.keycloakApi = keycloakApi;
    }

    @PostMapping(value = "/token", produces = "application/json")
    @Operation(summary = "Generate authentication token", description = "Authenticates a user with Keycloak and returns an access token. "
            +
            "This endpoint is used for user authentication and obtaining JWT tokens " +
            "for subsequent API calls. **Note: This endpoint does not require authentication.**", security = {}, requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User credentials for authentication", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenPostRequest.class))))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenPostResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed - invalid credentials", content = @Content(mediaType = "application/json", schema = @Schema(implementation = KeycloakErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - missing or invalid parameters", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = KeycloakErrorResponse.class)))
    })
    public Mono<ResponseEntity<Object>> postRegistration(
            @Parameter(description = "User credentials for authentication", required = true) @RequestBody TokenPostRequest request) {
        return keycloakApi.generateKeycloakToken(request)
                .map(tokenResponse -> ResponseEntity.ok((Object) tokenResponse))
                .onErrorResume(ResponseStatusException.class, ex -> {
                    log.error("Keycloak authentication failed: {}", ex.getReason(), ex);
                    return Mono.just(ResponseEntity.status(ex.getStatusCode())
                            .body((Object) ex.getReason()));
                })
                .onErrorResume(Exception.class, ex -> {
                    log.error("Unexpected error during authentication", ex);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body((Object) "Internal server error"));
                });
    }
}
