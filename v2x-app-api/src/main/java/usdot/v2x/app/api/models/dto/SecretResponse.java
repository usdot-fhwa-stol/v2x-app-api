package usdot.v2x.app.api.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecretResponse {
    @JsonProperty("iss_scms_token")
    private String issScmsToken;

    @JsonProperty("s3")
    private S3Config s3;

    @JsonProperty("mapbox_access_token")
    private String mapboxAccessToken;

    @JsonProperty("noaa_geomag_api_token")
    private String noaaGeomagApiToken;
}
