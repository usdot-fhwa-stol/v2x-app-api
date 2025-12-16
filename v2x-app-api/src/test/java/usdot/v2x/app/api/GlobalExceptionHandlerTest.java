package usdot.v2x.app.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;

import usdot.v2x.app.api.services.ErrorLoggingService;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private ErrorLoggingService errorLoggingService;

    private GlobalExceptionHandler globalExceptionHandler;

    @BeforeEach
    void setUp() {
        globalExceptionHandler = new GlobalExceptionHandler(errorLoggingService);
    }

    @Test
    void handleAuthorizationDeniedException_ShouldReturn403Forbidden() {
        // Arrange
        AuthorizationDeniedException ex = mock(AuthorizationDeniedException.class);
        when(ex.getMessage()).thenReturn("Access denied");

        // Act
        ResponseEntity<Object> response = globalExceptionHandler.handleAuthorizationDeniedException(ex);

        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(errorLoggingService).logErrorFromRequest(eq("AUTHORIZATION_DENIED"), eq(ex), any());
    }

    @Test
    void handleAccessDeniedException_ShouldReturn403Forbidden() {
        // Arrange
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        // Act
        ResponseEntity<Object> response = globalExceptionHandler.handleAccessDeniedException(ex);

        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(errorLoggingService).logErrorFromRequest(eq("ACCESS_DENIED"), eq(ex), any());
    }

    @Test
    void handleAuthenticationCredentialsNotFoundException_ShouldReturn401Unauthorized() {
        // Arrange
        AuthenticationCredentialsNotFoundException ex = new AuthenticationCredentialsNotFoundException(
                "Authentication required");

        // Act
        ResponseEntity<Object> response = globalExceptionHandler.handleAuthenticationCredentialsNotFoundException(ex);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(errorLoggingService).logErrorFromRequest(eq("AUTHENTICATION_REQUIRED"), eq(ex), any());
    }

    @Test
    void handleBadCredentialsException_ShouldReturn401Unauthorized() {
        // Arrange
        BadCredentialsException ex = new BadCredentialsException("Invalid credentials");

        // Act
        ResponseEntity<Object> response = globalExceptionHandler.handleBadCredentialsException(ex);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(errorLoggingService).logErrorFromRequest(eq("BAD_CREDENTIALS"), eq(ex), any());
    }
}
