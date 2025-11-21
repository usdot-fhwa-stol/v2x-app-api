package usdot.v2x.app.api.etx;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Thread-safe token storage component for ETX authentication tokens.
 */
@Component
@Slf4j
public class TokenStore {

    private String accessToken;
    private Instant accessTokenExpirationTime;
    private String sessionToken;
    private Instant sessionTokenExpirationTime;
    private Instant lastSessionTokenActivity;
    private static final long SESSION_INACTIVITY_TIMEOUT_MINUTES = 20;

    public synchronized String getAccessToken() {
        return accessToken;
    }

    public synchronized void setAccessToken(String accessToken, Instant expirationTime) {
        log.debug("Setting access token expiration time: {}", expirationTime);
        this.accessToken = accessToken;
        this.accessTokenExpirationTime = expirationTime;
    }

    public synchronized String getSessionToken() {
        if (sessionToken != null) {
            updateSessionTokenActivity();
        }
        return sessionToken;
    }

    public synchronized void setSessionToken(String sessionToken, Instant expirationTime) {
        log.debug("Setting session token expiration time: {}", expirationTime);
        this.sessionToken = sessionToken;
        this.sessionTokenExpirationTime = expirationTime;
        updateSessionTokenActivity();
    }

    private synchronized void updateSessionTokenActivity() {
        this.lastSessionTokenActivity = Instant.now();
    }

    private synchronized boolean isAccessTokenValid() {
        return accessTokenExpirationTime != null && Instant.now().isBefore(accessTokenExpirationTime);
    }

    // Added logic to support this documentation:
    // https://thingspace.verizon.com/documentation/api-documentation.html#/http/quick-start/credentials-and-tokens/obtaining-a-vz-m2m-sessiontoken-programmatically
    // The token will remain valid as long as your application continues to use it,
    // but it will expire after 20 minutes of inactivity.
    private synchronized boolean isSessionTokenValid() {
        if (sessionTokenExpirationTime == null || sessionToken == null || lastSessionTokenActivity == null) {
            return false;
        }

        Instant now = Instant.now();
        boolean notExpired = now.isBefore(sessionTokenExpirationTime);
        boolean notInactive = lastSessionTokenActivity.plus(SESSION_INACTIVITY_TIMEOUT_MINUTES, ChronoUnit.MINUTES)
                .isAfter(now);

        return notExpired && notInactive;
    }

    public synchronized boolean areTokensValid() {
        return isAccessTokenValid() && isSessionTokenValid();
    }
}
