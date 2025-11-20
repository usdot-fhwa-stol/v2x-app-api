package usdot.v2x.app.api.tim;

import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.geofence.TimConfigurationResponse;
import usdot.v2x.app.api.services.TimConfigurationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/prd/v2/tim")
@Tag(name = "TIM Configuration", description = "TIM ITIS phrases and metadata configuration endpoints")
public class TimConfigurationRestController {

    private final TimConfigurationService timConfigurationService;

    public TimConfigurationRestController(TimConfigurationService timConfigurationService) {
        this.timConfigurationService = timConfigurationService;
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping(value = "/configuration", produces = "application/json")
    @Operation(summary = "Get TIM configuration", description = "Retrieves the TIM ITIS phrases and metadata configuration. "
            +
            "This endpoint returns the configuration JSON containing TIM phrases, their codes, graphics, and overlay information "
            +
            "along with a version number that correlates to a downloadable ZIP file of icons.", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "TIM configuration retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TimConfigurationResponse.class))),
            @ApiResponse(responseCode = "404", description = "TIM configuration file not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Mono<ResponseEntity<TimConfigurationResponse>> getTimConfiguration() {
        try {
            return timConfigurationService.getTimConfiguration()
                    .map(config -> ResponseEntity.ok(config))
                    .onErrorResume(throwable -> {
                        log.error("Error retrieving TIM configuration", throwable);
                        return Mono.error(new RuntimeException(
                                "Failed to retrieve TIM configuration: " + throwable.getMessage()));
                    });
        } catch (Exception e) {
            log.error("Unexpected error in getTimConfiguration", e);
            return Mono.error(new RuntimeException("An unexpected error occurred: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping(value = "/icons/{version}", produces = "application/gzip")
    @Operation(summary = "Download TIM icons TAR.GZ", description = "Downloads a TAR.GZ file containing all TIM icons for the specified version. "
            +
            "This endpoint returns all available icons for the given version in a compressed tar.gz archive.", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "TIM icons TAR.GZ file downloaded successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Mono<ResponseEntity<byte[]>> downloadTimIcons(@PathVariable("version") String version) {
        try {
            return timConfigurationService.getTimIconsTarGz(version)
                    .map(iconsData -> {
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                        headers.setContentDisposition(
                                ContentDisposition.attachment().filename("tim-icons-v" + version + ".tar.gz").build());
                        headers.setContentLength(iconsData.length);

                        return ResponseEntity.ok()
                                .headers(headers)
                                .body(iconsData);
                    })
                    .onErrorResume(throwable -> {
                        log.error("Error downloading TIM icons for version: " + version, throwable);
                        return Mono
                                .error(new RuntimeException("Failed to download TIM icons for version " + version + ": "
                                        + throwable.getMessage()));
                    });
        } catch (Exception e) {
            log.error("Unexpected error in downloadTimIcons for version: " + version, e);
            return Mono.error(new RuntimeException("An unexpected error occurred: " + e.getMessage()));
        }
    }
}
