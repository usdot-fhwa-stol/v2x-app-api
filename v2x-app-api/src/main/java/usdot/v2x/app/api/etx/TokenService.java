package usdot.v2x.app.api.etx;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import usdot.v2x.app.api.config.etx.ThingspaceProperties;

import java.time.Duration;
import java.time.Instant;

/**
 * Service for managing ETX authentication tokens.
 */
@Slf4j
@Service
public class TokenService {

    private final ThingspaceApi thingspaceApi;
    private final TokenStore tokenStore;
    private final Duration sessionTokenLifespan;
    private final Boolean enabled;

    public TokenService(ThingspaceProperties thingspaceProperties, ThingspaceApi thingspaceApi, TokenStore tokenStore) {
        this.thingspaceApi = thingspaceApi;
        this.tokenStore = tokenStore;
        this.sessionTokenLifespan = thingspaceProperties.getSessionTokenLifespan();
        this.enabled = thingspaceProperties.getEnabled();
    }

    private Mono<TokenStore> refreshTokens() {
        if (!enabled) {
            log.debug("Thingspace is disabled, returning existing token store");
            return Mono.just(tokenStore);
        }
        return thingspaceApi.generateAccessToken()
                .flatMap(accessToken -> {
                    tokenStore.setAccessToken(accessToken.getAccess_token(),
                            Instant.now().plusSeconds(accessToken.getExpires_in()));
                    return thingspaceApi.generateSessionToken(accessToken.getAccess_token()).map(sessionToken -> {
                        tokenStore.setSessionToken(sessionToken.getSessionToken(),
                                Instant.now().plus(sessionTokenLifespan));
                        return tokenStore;
                    });
                })
                .doOnSuccess(v -> log.info("Successfully refreshed tokens"))
                .doOnError(error -> log.warn("Failed to refresh tokens: {}", error.getMessage()));
    }

    public Mono<TokenStore> getTokenStore() {
        if (!enabled) {
            log.debug("Thingspace is disabled, returning existing token store");
            return Mono.just(tokenStore);
        }
        if (!tokenStore.areTokensValid()) {
            log.debug("Refreshing Tokens - Returning Store {}", tokenStore.areTokensValid());
            return refreshTokens();
        } else {
            log.debug("Returning Store {}", tokenStore.areTokensValid());
            return Mono.just(tokenStore);
        }
    }

    @Scheduled(fixedRateString = "${thingspace.sessionTokenLifespan}")
    @ConditionalOnProperty(value = { "thingspace.tokenPeriodicRegenerationEnabled" }, havingValue = "true")
    public void refreshTokensPeriodically() {
        if (!enabled) {
            log.debug("Thingspace is disabled, skipping periodic token refresh");
            return;
        }
        log.debug("Starting periodic token refresh");
        refreshTokens()
                .doOnSuccess(v -> log.info("Periodic token refresh completed successfully"))
                .doOnError(error -> log.warn("Periodic token refresh failed: {}", error.getMessage()))
                .subscribe();
    }
}
