package usdot.v2x.app.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import usdot.v2x.app.api.models.etx.ErrorResponseException;
import usdot.v2x.app.api.services.ErrorLoggingService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import reactor.netty.http.client.PrematureCloseException;
import java.util.concurrent.TimeoutException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;
import usdot.v2x.app.api.exceptions.NoAvailableGeohashException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private final ErrorLoggingService errorLoggingService;

    public GlobalExceptionHandler(ErrorLoggingService errorLoggingService) {
        this.errorLoggingService = errorLoggingService;
    }

    // Handle 400 bad request exceptions for failed JSON parsing
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("error", ex.getMessage());
        errorDetails.put("description", "Invalid JSON payload");

        // Log to database only if it's from an API request
        errorLoggingService.logErrorFromRequest("HTTP_MESSAGE_NOT_READABLE", ex,
                ErrorLoggingService.ErrorSeverity.MEDIUM);

        log.error("Invalid JSON payload", ex);
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<Object> handleApiException(ErrorResponseException ex) {
        // Log to database only if it's from an API request
        errorLoggingService.logErrorFromRequest("API_ERROR", ex, ErrorLoggingService.ErrorSeverity.HIGH);

        log.error("Forwarding error response", ex);
        // Build a JSON response with the exception details
        return ResponseEntity.status(ex.getStatusCode())
                .body(ex.getErrorResponse());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        // Log to database only if it's from an API request
        errorLoggingService.logErrorFromRequest("ILLEGAL_ARGUMENT", ex, ErrorLoggingService.ErrorSeverity.MEDIUM);

        log.error("Invalid argument", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatusException(ResponseStatusException ex) {
        // Log to database only if it's from an API request
        errorLoggingService.logErrorFromRequest("RESPONSE_STATUS_ERROR", ex, ErrorLoggingService.ErrorSeverity.MEDIUM);

        log.error("Response status exception: {}", ex.getReason(), ex);
        return ResponseEntity.status(ex.getStatusCode())
                .body(ex.getReason());
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Object> handleAuthorizationDeniedException(AuthorizationDeniedException ex) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("error", "Access Denied");
        errorDetails.put("description", "Insufficient permissions to access this resource");

        // Log to database only if it's from an API request
        errorLoggingService.logErrorFromRequest("AUTHORIZATION_DENIED", ex, ErrorLoggingService.ErrorSeverity.MEDIUM);

        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorDetails);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("error", "Access Denied");
        errorDetails.put("description", "Insufficient permissions to access this resource");

        // Log to database only if it's from an API request
        errorLoggingService.logErrorFromRequest("ACCESS_DENIED", ex, ErrorLoggingService.ErrorSeverity.MEDIUM);

        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorDetails);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<Object> handleAuthenticationCredentialsNotFoundException(
            AuthenticationCredentialsNotFoundException ex) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("error", "Authentication Required");
        errorDetails.put("description", "Valid authentication credentials are required to access this resource");

        // Log to database only if it's from an API request
        errorLoggingService.logErrorFromRequest("AUTHENTICATION_REQUIRED", ex,
                ErrorLoggingService.ErrorSeverity.MEDIUM);

        log.warn("Authentication required: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(errorDetails);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentialsException(BadCredentialsException ex) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("error", "Invalid Credentials");
        errorDetails.put("description", "The provided authentication credentials are invalid");

        // Log to database only if it's from an API request
        errorLoggingService.logErrorFromRequest("BAD_CREDENTIALS", ex, ErrorLoggingService.ErrorSeverity.MEDIUM);

        log.warn("Bad credentials: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(errorDetails);
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleAsyncRequestTimeoutException(AsyncRequestTimeoutException ex) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("error", "Request Timeout");
        errorDetails.put("description", "The request timed out while waiting for a response from the external service");
        errorDetails.put("details", "This may be due to network issues or the external service being slow to respond");

        // Log to database only if it's from an API request
        errorLoggingService.logErrorFromRequest("ASYNC_REQUEST_TIMEOUT", ex, ErrorLoggingService.ErrorSeverity.HIGH);

        log.error("Async request timeout: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(errorDetails, HttpStatus.REQUEST_TIMEOUT);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeoutException(TimeoutException ex) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("error", "Request Timeout");
        errorDetails.put("description", "The request timed out while waiting for a response");
        errorDetails.put("details", "The operation took longer than the configured timeout period");

        // Log to database only if it's from an API request
        errorLoggingService.logErrorFromRequest("TIMEOUT_EXCEPTION", ex, ErrorLoggingService.ErrorSeverity.HIGH);

        log.error("Timeout exception: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(errorDetails, HttpStatus.REQUEST_TIMEOUT);
    }

    @ExceptionHandler(PrematureCloseException.class)
    public ResponseEntity<Map<String, Object>> handlePrematureCloseException(PrematureCloseException ex) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("error", "Connection Closed");
        errorDetails.put("description", "The connection was closed prematurely by the external service");
        errorDetails.put("details", "This may indicate network issues or the external service is unavailable");

        // Log to database only if it's from an API request
        errorLoggingService.logErrorFromRequest("PREMATURE_CLOSE", ex, ErrorLoggingService.ErrorSeverity.HIGH);

        log.error("Premature close exception: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(errorDetails, HttpStatus.SERVICE_UNAVAILABLE);
    }

    // Handle 404 Not Found for undefined endpoints
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFoundException(NoHandlerFoundException ex) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("error", "Not Found");
        errorDetails.put("description", "The requested endpoint was not found");
        errorDetails.put("path", ex.getRequestURL());
        errorDetails.put("method", ex.getHttpMethod());

        // Log to database only if it's from an API request
        errorLoggingService.logErrorFromRequest("NO_HANDLER_FOUND", ex, ErrorLoggingService.ErrorSeverity.LOW);

        log.warn("No handler found for {} {}", ex.getHttpMethod(), ex.getRequestURL());
        return new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND);
    }

    // Catch-all handler for any unhandled exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("error", "Internal server error");
        errorDetails.put("description", "An unexpected error occurred");

        // Log to database as critical only if it's from an API request
        errorLoggingService.logCriticalErrorFromRequest("UNHANDLED_EXCEPTION", ex);

        log.error("Unhandled exception", ex);
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Handle geohash selection saturation from deposit flow as 409 instead of 500
    @ExceptionHandler(NoAvailableGeohashException.class)
    public ResponseEntity<Map<String, Object>> handleNoAvailableGeohashException(NoAvailableGeohashException ex) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("error", "NO_AVAILABLE_GEOHASH");
        errorDetails.put("description", ex.getMessage());

        errorLoggingService.logErrorFromRequest("NO_AVAILABLE_GEOHASH", ex,
                ErrorLoggingService.ErrorSeverity.HIGH);

        log.warn("Deposit rejected due to saturated geohash area: {}", ex.getMessage());
        return new ResponseEntity<>(errorDetails, HttpStatus.CONFLICT);
    }
}