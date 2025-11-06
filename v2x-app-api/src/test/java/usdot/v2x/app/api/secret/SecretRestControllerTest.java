package usdot.v2x.app.api.secret;

import usdot.v2x.app.api.models.dto.SecretResponse;
import usdot.v2x.app.api.models.dto.S3Config;
import usdot.v2x.app.api.services.SecretService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SecretRestController.class)
class SecretRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SecretService secretService;

    @Test
    void getSecretConfig_ShouldReturnSuccess() throws Exception {
        // Given
        S3Config s3Config = new S3Config("test-key", "test-secret", "test-bucket", "us-east-1", "test-destination");
        SecretResponse secretResponse = new SecretResponse(
                "test-token",
                s3Config,
                "test-mapbox-token",
                "test-noaa-token");

        when(secretService.getSecretConfig()).thenReturn(Mono.just(secretResponse));

        // When & Then
        mockMvc.perform(get("/prd/v2/secrets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iss_scms_token").value("test-token"))
                .andExpect(jsonPath("$.s3.s3_access_key").value("test-key"))
                .andExpect(jsonPath("$.s3.s3_secret_key").value("test-secret"))
                .andExpect(jsonPath("$.s3.s3_bucket_name").value("test-bucket"))
                .andExpect(jsonPath("$.s3.s3_region").value("us-east-1"))
                .andExpect(jsonPath("$.s3.s3_destination").value("test-destination"))
                .andExpect(jsonPath("$.mapbox_access_token").value("test-mapbox-token"))
                .andExpect(jsonPath("$.noaa_geomag_api_token").value("test-noaa-token"));
    }

    @Test
    void getSecretConfig_WhenServiceThrowsException_ShouldReturnError() throws Exception {
        // Given
        when(secretService.getSecretConfig()).thenReturn(Mono.error(new RuntimeException("Service error")));

        // When & Then
        mockMvc.perform(get("/prd/v2/secrets"))
                .andExpect(status().isInternalServerError());
    }
}
