package usdot.v2x.app.api.etx;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import usdot.v2x.app.api.config.etx.ThingspaceProperties;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TokenService {

    private final ThingspaceApi thingspaceApi;
    private final TokenStore tokenStore;
    private final Double sessionTokenLifespanMinutes;

    public TokenService(ThingspaceProperties thingspaceProperties, ThingspaceApi thingspaceApi, TokenStore tokenStore) {
        this.thingspaceApi = thingspaceApi;
        this.tokenStore = tokenStore;
        this.sessionTokenLifespanMinutes = thingspaceProperties.getSessionTokenLifespanMinutes();
    }

    private Mono<TokenStore> refreshTokens() {
        return thingspaceApi.generateAccessToken()
                .flatMap(accessToken -> {
                    tokenStore.setAccessToken(accessToken.getAccess_token(),
                            Instant.now().plusSeconds(accessToken.getExpires_in()));
                    return thingspaceApi.generateSessionToken(accessToken.getAccess_token()).map(sessionToken -> {
                        tokenStore.setSessionToken(sessionToken.getSessionToken(),
                                Instant.now().plusSeconds(sessionTokenLifespanMinutes.longValue() * 60));
                        return tokenStore;
                    });
                })
                .doOnSuccess(v -> log.info("Successfully refreshed tokens"))
                .doOnError(error -> log.warn("Failed to refresh tokens: {}", error.getMessage()));
    }

    public Mono<TokenStore> getTokenStore() {
        if (!tokenStore.areTokensValid()) {
            log.debug("Refreshing Tokens - Returning Store {}", tokenStore.areTokensValid());
            return refreshTokens();
        } else {
            log.debug("Returning Store {}", tokenStore.areTokensValid());
            return Mono.just(tokenStore);
        }
    }

    @Scheduled(fixedRateString = "${thingspace.sessionTokenLifespanMinutes}", timeUnit = TimeUnit.MINUTES)
    @ConditionalOnProperty(value = { "thingspace.tokenPeriodicRegenerationEnabled" }, havingValue = "true")
    public Mono<TokenStore> refreshTokensPeriodically() {
        log.debug("Starting periodic token refresh");
        return refreshTokens()
                .doOnSuccess(v -> log.info("Periodic token refresh completed successfully"))
                .doOnError(error -> log.warn("Periodic token refresh failed: {}", error.getMessage()));
    }
}
