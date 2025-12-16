package usdot.v2x.app.api.etx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class TokenStoreTest {

    private TokenStore tokenStore;

    @BeforeEach
    void setUp() {
        tokenStore = new TokenStore();
    }

    @Test
    void whenTokensAreSet_thenTheyAreValid() {
        // Given
        Instant now = Instant.now();
        String accessToken = "test-access-token";
        String sessionToken = "test-session-token";

        // When
        tokenStore.setAccessToken(accessToken, now.plusSeconds(3600));
        tokenStore.setSessionToken(sessionToken, now.plusSeconds(3600));

        // Then
        assertTrue(tokenStore.areTokensValid());
        assertEquals(accessToken, tokenStore.getAccessToken());
        assertEquals(sessionToken, tokenStore.getSessionToken());
    }

    @Test
    void whenAccessTokenExpires_thenTokensAreInvalid() {
        // Given
        Instant now = Instant.now();
        tokenStore.setAccessToken("test-access-token", now.minusSeconds(1));
        tokenStore.setSessionToken("test-session-token", now.plusSeconds(3600));

        // Then
        assertFalse(tokenStore.areTokensValid());
    }

    @Test
    void whenSessionTokenExpires_thenTokensAreInvalid() {
        // Given
        Instant now = Instant.now();
        tokenStore.setAccessToken("test-access-token", now.plusSeconds(3600));
        tokenStore.setSessionToken("test-session-token", now.minusSeconds(1));

        // Then
        assertFalse(tokenStore.areTokensValid());
    }

    @Test
    void whenSessionTokenInactive_thenTokensAreInvalid() {
        // Given
        Instant now = Instant.now();
        tokenStore.setAccessToken("test-access-token", now.plusSeconds(3600));
        tokenStore.setSessionToken("test-session-token", now.plusSeconds(3600));

        // When - simulate inactivity by moving time forward
        tokenStore.getSessionToken(); // Updates last activity
        try {
            Thread.sleep(1); // Ensure some time passes
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Use reflection to modify lastSessionTokenActivity
        try {
            java.lang.reflect.Field field = TokenStore.class.getDeclaredField("lastSessionTokenActivity");
            field.setAccessible(true);
            field.set(tokenStore, now.minus(21, ChronoUnit.MINUTES));
        } catch (Exception e) {
            fail("Could not set lastSessionTokenActivity");
        }

        // Then
        assertFalse(tokenStore.areTokensValid());
    }

    @Test
    void whenSessionTokenAccessed_thenActivityTimeIsUpdated() {
        // Given
        Instant now = Instant.now();
        tokenStore.setAccessToken("test-access-token", now.plusSeconds(3600));
        tokenStore.setSessionToken("test-session-token", now.plusSeconds(3600));

        // When
        String token = tokenStore.getSessionToken();

        // Then
        assertNotNull(token);
        assertTrue(tokenStore.areTokensValid());
    }
}