package usdot.v2x.app.api.services;

import usdot.v2x.app.api.models.geofence.TimConfigurationResponse;

import reactor.core.publisher.Mono;

public interface TimConfigurationService {
    Mono<TimConfigurationResponse> getTimConfiguration();

    Mono<byte[]> getTimIconsTarGz(String version);
}
