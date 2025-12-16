package usdot.v2x.app.api.services;

import usdot.v2x.app.api.models.dto.SecretResponse;
import reactor.core.publisher.Mono;

public interface SecretService {

    /**
     * Get secret configuration
     */
    Mono<SecretResponse> getSecretConfig();
}
