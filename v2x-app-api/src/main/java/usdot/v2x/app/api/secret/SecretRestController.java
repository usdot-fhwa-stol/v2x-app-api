package usdot.v2x.app.api.secret;

import usdot.v2x.app.api.models.dto.SecretResponse;
import usdot.v2x.app.api.services.SecretService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/prd/v2")
@Tag(name = "Secret Management", description = "Secret configuration endpoints")
public class SecretRestController {

    private final SecretService secretService;

    public SecretRestController(SecretService secretService) {
        this.secretService = secretService;
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping(value = "/secrets", produces = "application/json")
    @Operation(summary = "Get secret configuration", description = "Retrieves secret configuration including tokens and S3 settings.", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Secret configuration retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SecretResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = usdot.v2x.app.api.models.etx.ErrorResponse.class)))
    })
    public Mono<SecretResponse> getSecretConfig() {
        try {
            return secretService.getSecretConfig()
                    .onErrorResume(throwable -> {
                        log.error("Error retrieving secret configuration", throwable);
                        return Mono.error(new RuntimeException(
                                "Failed to retrieve secret configuration: " + throwable.getMessage()));
                    });
        } catch (Exception e) {
            log.error("Unexpected error in getSecretConfig", e);
            return Mono.error(new RuntimeException("An unexpected error occurred: " + e.getMessage()));
        }
    }
}
