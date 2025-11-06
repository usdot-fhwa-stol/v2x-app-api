package usdot.v2x.app.api.services;

import usdot.v2x.app.api.models.dto.SecretResponse;
import usdot.v2x.app.api.models.dto.S3Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class SecretServiceImpl implements SecretService {

    @Value("${secret.iss-scms-token:token}")
    private String issScmsToken;

    @Value("${secret.s3.access-key:key}")
    private String s3AccessKey;

    @Value("${secret.s3.secret-key:key}")
    private String s3SecretKey;

    @Value("${secret.s3.bucket-name:name}")
    private String s3BucketName;

    @Value("${secret.s3.region:region}")
    private String s3Region;

    @Value("${secret.s3.destination:destination}")
    private String s3Destination;

    @Value("${secret.mapbox-access-token:mapbox_access_token}")
    private String mapboxAccessToken;

    @Value("${secret.noaa-geomag-api-token:ZNEw7}")
    private String noaaGeomagApiToken;

    @Override
    public Mono<SecretResponse> getSecretConfig() {
        return Mono.fromCallable(() -> {
            log.info("Retrieving secret configuration");

            S3Config s3Config = new S3Config(
                    s3AccessKey,
                    s3SecretKey,
                    s3BucketName,
                    s3Region,
                    s3Destination);

            SecretResponse response = new SecretResponse(
                    issScmsToken,
                    s3Config,
                    mapboxAccessToken,
                    noaaGeomagApiToken);

            log.info("Secret configuration retrieved successfully");
            return response;
        });
    }
}
