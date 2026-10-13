package usdot.v2x.app.api.etx;

import usdot.v2x.app.api.config.etx.ThingspaceProperties;
import usdot.v2x.app.api.models.etx.AuthToken;
import usdot.v2x.app.api.models.etx.SessionToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private ThingspaceApi thingspaceApi;

    @Mock
    private TokenStore tokenStore;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        ThingspaceProperties properties = new ThingspaceProperties();
        properties.setSessionTokenLifespan(Duration.ofMinutes(60));
        properties.setEnabled(true);
        tokenService = new TokenService(properties, thingspaceApi, tokenStore);
    }

    @Test
    void whenTokensAreValid_thenNoRefreshNeeded() {
        // Given
        when(tokenStore.areTokensValid()).thenReturn(true);

        // When
        Mono<TokenStore> result = tokenService.getTokenStore();

        // Then
        StepVerifier.create(result)
                .expectNext(tokenStore)
                .verifyComplete();

        verify(thingspaceApi, never()).generateAccessToken();
        verify(thingspaceApi, never()).generateSessionToken(anyString());
    }

    @Test
    void whenTokensAreInvalid_thenRefreshTokens() {
        // Given
        AuthToken authToken = new AuthToken();
        authToken.setAccess_token("new-access-token");
        authToken.setExpires_in(3600);

        SessionToken sessionToken = new SessionToken();
        sessionToken.setSessionToken("new-session-token");

        when(tokenStore.areTokensValid()).thenReturn(false);
        when(thingspaceApi.generateAccessToken()).thenReturn(Mono.just(authToken));
        when(thingspaceApi.generateSessionToken(anyString())).thenReturn(Mono.just(sessionToken));

        // When
        Mono<TokenStore> result = tokenService.getTokenStore();

        // Then
        StepVerifier.create(result)
                .expectNext(tokenStore)
                .verifyComplete();

        verify(thingspaceApi).generateAccessToken();
        verify(thingspaceApi).generateSessionToken(authToken.getAccess_token());
        verify(tokenStore).setAccessToken(eq(authToken.getAccess_token()), any(Instant.class));
        verify(tokenStore).setSessionToken(eq(sessionToken.getSessionToken()), any(Instant.class));
    }

    @Test
    void whenRefreshTokensFails_thenPropagateError() {
        // Given
        when(tokenStore.areTokensValid()).thenReturn(false);
        when(thingspaceApi.generateAccessToken()).thenReturn(Mono.error(new RuntimeException("API Error")));

        // When
        Mono<TokenStore> result = tokenService.getTokenStore();

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void whenPeriodicRefreshCalled_thenRefreshTokens() {
        // Given
        AuthToken authToken = new AuthToken();
        authToken.setAccess_token("new-access-token");
        authToken.setExpires_in(3600);

        SessionToken sessionToken = new SessionToken();
        sessionToken.setSessionToken("new-session-token");

        when(thingspaceApi.generateAccessToken()).thenReturn(Mono.just(authToken));
        when(thingspaceApi.generateSessionToken(anyString())).thenReturn(Mono.just(sessionToken));

        // When
        tokenService.refreshTokensPeriodically();

        // Then
        verify(thingspaceApi).generateAccessToken();
        verify(thingspaceApi).generateSessionToken(authToken.getAccess_token());
    }

    @Test
    void whenConcurrentCallersFindInvalidTokens_thenOnlyOneRefreshIsIssued() {
        // Given: the access token request stays in flight until we complete it
        AuthToken authToken = new AuthToken();
        authToken.setAccess_token("new-access-token");
        authToken.setExpires_in(3600);
        SessionToken sessionToken = new SessionToken();
        sessionToken.setSessionToken("new-session-token");

        Sinks.One<AuthToken> pendingAccessToken = Sinks.one();
        when(tokenStore.areTokensValid()).thenReturn(false);
        when(thingspaceApi.generateAccessToken()).thenReturn(pendingAccessToken.asMono());
        when(thingspaceApi.generateSessionToken(anyString())).thenReturn(Mono.just(sessionToken));

        // When: three requests arrive while the refresh is in flight
        List<TokenStore> results = new CopyOnWriteArrayList<>();
        tokenService.getTokenStore().subscribe(results::add);
        tokenService.getTokenStore().subscribe(results::add);
        tokenService.getTokenStore().subscribe(results::add);
        pendingAccessToken.tryEmitValue(authToken);

        // Then: all three get the refreshed store from a single login
        assertEquals(3, results.size());
        verify(thingspaceApi, times(1)).generateAccessToken();
        verify(thingspaceApi, times(1)).generateSessionToken("new-access-token");
    }

    @Test
    void whenRefreshCompletes_thenNextInvalidCheckStartsANewRefresh() {
        AuthToken authToken = new AuthToken();
        authToken.setAccess_token("new-access-token");
        authToken.setExpires_in(3600);
        SessionToken sessionToken = new SessionToken();
        sessionToken.setSessionToken("new-session-token");

        when(tokenStore.areTokensValid()).thenReturn(false);
        when(thingspaceApi.generateAccessToken()).thenReturn(Mono.just(authToken));
        when(thingspaceApi.generateSessionToken(anyString())).thenReturn(Mono.just(sessionToken));

        StepVerifier.create(tokenService.getTokenStore()).expectNext(tokenStore).verifyComplete();
        StepVerifier.create(tokenService.getTokenStore()).expectNext(tokenStore).verifyComplete();

        // A completed refresh is not cached forever
        verify(thingspaceApi, times(2)).generateAccessToken();
    }

    @Test
    void whenRefreshFails_thenNextCallRetries() {
        AuthToken authToken = new AuthToken();
        authToken.setAccess_token("new-access-token");
        authToken.setExpires_in(3600);
        SessionToken sessionToken = new SessionToken();
        sessionToken.setSessionToken("new-session-token");

        when(tokenStore.areTokensValid()).thenReturn(false);
        when(thingspaceApi.generateAccessToken())
                .thenReturn(Mono.error(new RuntimeException("API Error")))
                .thenReturn(Mono.just(authToken));
        when(thingspaceApi.generateSessionToken(anyString())).thenReturn(Mono.just(sessionToken));

        StepVerifier.create(tokenService.getTokenStore()).expectError(RuntimeException.class).verify();
        // The failed refresh must not be cached
        StepVerifier.create(tokenService.getTokenStore()).expectNext(tokenStore).verifyComplete();
    }

    @Test
    void whenPeriodicRegenerationDisabled_thenSkipPeriodicRefresh() {
        ThingspaceProperties properties = new ThingspaceProperties();
        properties.setSessionTokenLifespan(Duration.ofMinutes(60));
        properties.setEnabled(true);
        properties.setTokenPeriodicRegenerationEnabled(false);
        TokenService service = new TokenService(properties, thingspaceApi, tokenStore);

        service.refreshTokensPeriodically();

        verify(thingspaceApi, never()).generateAccessToken();
    }

    @Test
    void whenThingspaceDisabled_thenReturnTokenStoreWithoutRefresh() {
        // Given
        ThingspaceProperties disabledProperties = new ThingspaceProperties();
        disabledProperties.setSessionTokenLifespan(Duration.ofMinutes(60));
        disabledProperties.setEnabled(false);
        TokenService disabledTokenService = new TokenService(disabledProperties, thingspaceApi, tokenStore);

        // When
        Mono<TokenStore> result = disabledTokenService.getTokenStore();

        // Then
        StepVerifier.create(result)
                .expectNext(tokenStore)
                .verifyComplete();

        verify(thingspaceApi, never()).generateAccessToken();
        verify(thingspaceApi, never()).generateSessionToken(anyString());
    }

    @Test
    void whenThingspaceDisabled_thenSkipPeriodicRefresh() {
        // Given
        ThingspaceProperties disabledProperties = new ThingspaceProperties();
        disabledProperties.setSessionTokenLifespan(Duration.ofMinutes(60));
        disabledProperties.setEnabled(false);
        TokenService disabledTokenService = new TokenService(disabledProperties, thingspaceApi, tokenStore);

        // When
        disabledTokenService.refreshTokensPeriodically();

        // Then
        verify(thingspaceApi, never()).generateAccessToken();
        verify(thingspaceApi, never()).generateSessionToken(anyString());
    }
}