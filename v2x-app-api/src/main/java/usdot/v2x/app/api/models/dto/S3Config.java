package usdot.v2x.app.api.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class S3Config {
    @JsonProperty("s3_access_key")
    private String s3AccessKey;

    @JsonProperty("s3_secret_key")
    private String s3SecretKey;

    @JsonProperty("s3_bucket_name")
    private String s3BucketName;

    @JsonProperty("s3_region")
    private String s3Region;

    @JsonProperty("s3_destination")
    private String s3Destination;
}
