package usdot.v2x.app.api.etx;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import usdot.v2x.app.api.config.etx.ThingspaceProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

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
    private final Boolean periodicRegenerationEnabled;
    private final AtomicReference<Mono<TokenStore>> inFlightRefresh = new AtomicReference<>();

    public TokenService(ThingspaceProperties thingspaceProperties, ThingspaceApi thingspaceApi, TokenStore tokenStore) {
        this.thingspaceApi = thingspaceApi;
        this.tokenStore = tokenStore;
        this.sessionTokenLifespan = thingspaceProperties.getSessionTokenLifespan();
        this.enabled = thingspaceProperties.getEnabled();
        this.periodicRegenerationEnabled = thingspaceProperties.getTokenPeriodicRegenerationEnabled();
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

    /**
     * Refresh tokens, sharing a single in-flight refresh between concurrent
     * callers so that N requests arriving with expired tokens issue one
     * session login rather than N competing ones.
     */
    private Mono<TokenStore> sharedRefresh() {
        Mono<TokenStore> existing = inFlightRefresh.get();
        if (existing != null) {
            return existing;
        }
        AtomicReference<Mono<TokenStore>> self = new AtomicReference<>();
        Mono<TokenStore> refresh = Mono.defer(this::refreshTokens)
                .doFinally(signal -> inFlightRefresh.compareAndSet(self.get(), null))
                .cache();
        self.set(refresh);
        if (inFlightRefresh.compareAndSet(null, refresh)) {
            return refresh;
        }
        Mono<TokenStore> winner = inFlightRefresh.get();
        return winner != null ? winner : refresh;
    }

    public Mono<TokenStore> getTokenStore() {
        if (!enabled) {
            log.debug("Thingspace is disabled, returning existing token store");
            return Mono.just(tokenStore);
        }
        if (!tokenStore.areTokensValid()) {
            log.debug("Refreshing Tokens - Returning Store {}", tokenStore.areTokensValid());
            return sharedRefresh();
        } else {
            log.debug("Returning Store {}", tokenStore.areTokensValid());
            return Mono.just(tokenStore);
        }
    }

    @Scheduled(fixedRateString = "${thingspace.sessionTokenLifespan}")
    public void refreshTokensPeriodically() {
        if (!enabled) {
            log.debug("Thingspace is disabled, skipping periodic token refresh");
            return;
        }
        if (Boolean.FALSE.equals(periodicRegenerationEnabled)) {
            log.debug("Periodic token regeneration is disabled, skipping");
            return;
        }
        log.debug("Starting periodic token refresh");
        sharedRefresh()
                .doOnSuccess(v -> log.info("Periodic token refresh completed successfully"))
                .doOnError(error -> log.warn("Periodic token refresh failed: {}", error.getMessage()))
                .subscribe();
    }
}
