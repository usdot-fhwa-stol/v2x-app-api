package usdot.v2x.app.api.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for logging errors to the database and application logs.
 */
@Service
@Slf4j
public class ErrorLoggingService {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    /**
     * Log an error to the database only if it's from an API request
     */
    public void logErrorFromRequest(String errorType, String errorMessage, String stackTrace, ErrorSeverity severity) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes == null) {
                log.debug("No request context found, skipping error logging");
                return;
            }

            HttpServletRequest request = attributes.getRequest();
            String requestPath = request.getRequestURI();

            // Only log errors from API requests (exclude health checks, actuator endpoints,
            // etc.)
            if (!isApiRequest(requestPath)) {
                log.debug("Skipping error logging for non-API request: {}", requestPath);
                return;
            }

            // Ensure error message is not null or empty
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "Unknown error occurred";
            }

            // Build error log data
            Map<String, Object> errorLogData = buildErrorLogData(errorType, errorMessage, stackTrace, severity,
                    request);

            // Log to database
            saveErrorLogToDatabase(errorLogData);

            log.debug("Error logged to database: {}", errorLogData.get("errorType"));
        } catch (Exception e) {
            log.error("Failed to log error to database", e);
        }
    }

    /**
     * Log an error with exception only if it's from an API request
     */
    public void logErrorFromRequest(String errorType, Exception exception, ErrorSeverity severity) {
        String errorMessage = exception.getMessage();
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            errorMessage = exception.getClass().getSimpleName() + " occurred";
        }

        logErrorFromRequest(
                errorType,
                errorMessage,
                getStackTraceAsString(exception),
                severity);
    }

    /**
     * Log a critical error only if it's from an API request
     */
    public void logCriticalErrorFromRequest(String errorType, String errorMessage, String stackTrace) {
        // Ensure error message is not null or empty for critical errors
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            errorMessage = "Critical error occurred";
        }
        logErrorFromRequest(errorType, errorMessage, stackTrace, ErrorSeverity.CRITICAL);
    }

    /**
     * Log a critical error with exception only if it's from an API request
     */
    public void logCriticalErrorFromRequest(String errorType, Exception exception) {
        logErrorFromRequest(errorType, exception, ErrorSeverity.CRITICAL);
    }

    /**
     * Check if the request path is an API request that should be logged
     */
    private boolean isApiRequest(String requestPath) {
        if (requestPath == null) {
            return false;
        }

        // Exclude health checks, actuator endpoints, and other system endpoints
        String[] excludedPaths = {
                "/actuator",
                "/health",
                "/metrics",
                "/info",
                "/swagger-ui",
                "/api-docs",
                "/favicon.ico",
                "/error"
        };

        for (String excludedPath : excludedPaths) {
            if (requestPath.startsWith(excludedPath)) {
                return false;
            }
        }

        // Only include actual API endpoints
        return requestPath.startsWith("/prd/") ||
                requestPath.startsWith("/api/") ||
                requestPath.startsWith("/auth/");
    }

    /**
     * Build error log data map
     */
    private Map<String, Object> buildErrorLogData(String errorType, String errorMessage, String stackTrace,
            ErrorSeverity severity, HttpServletRequest request) {
        Map<String, Object> errorLogData = new HashMap<>();

        errorLogData.put("timestamp", LocalDateTime.now());
        errorLogData.put("errorType", errorType);
        errorLogData.put("errorMessage", errorMessage);
        errorLogData.put("stackTrace", stackTrace);
        errorLogData.put("severity", severity.name());

        // Add request information
        addRequestInfo(errorLogData, request);

        return errorLogData;
    }

    /**
     * Add request information to the error log data
     */
    private void addRequestInfo(Map<String, Object> errorLogData, HttpServletRequest request) {
        try {
            errorLogData.put("requestPath", request.getRequestURI());
            errorLogData.put("requestMethod", request.getMethod());
            errorLogData.put("userAgent", request.getHeader("User-Agent"));
            errorLogData.put("clientIp", getClientIpAddress(request));

            // Capture request body for POST/PUT requests
            if ("POST".equalsIgnoreCase(request.getMethod()) || "PUT".equalsIgnoreCase(request.getMethod())) {
                errorLogData.put("requestBody", getRequestBody(request));
            }
        } catch (Exception e) {
            log.debug("Could not add request info to error log", e);
        }
    }

    /**
     * Get request body from the request
     */
    private String getRequestBody(HttpServletRequest request) {
        try {
            // Try to get the cached request body from our filter
            String requestBody = usdot.v2x.app.api.config.RequestBodyCaptureFilter.getRequestBody(request);
            if (requestBody != null) {
                return truncateRequestBody(requestBody);
            }

            // Fallback: try to read the request body directly
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            String fallbackRequestBody = sb.toString();
            return truncateRequestBody(fallbackRequestBody);

        } catch (IOException e) {
            log.debug("Could not read request body", e);
            return "[Could not read request body]";
        }
    }

    /**
     * Truncate request body if it's too long
     */
    private String truncateRequestBody(String requestBody) {
        if (requestBody == null) {
            return null;
        }

        // Limit the size to prevent database issues
        if (requestBody.length() > 10000) {
            return requestBody.substring(0, 10000) + "... [truncated]";
        }

        return requestBody;
    }

    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0];
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * Convert exception stack trace to string
     */
    private String getStackTraceAsString(Exception exception) {
        if (exception == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(exception.toString()).append("\n");

        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Save error log to database using JdbcTemplate
     */
    private void saveErrorLogToDatabase(Map<String, Object> errorLogData) {
        // Skip database logging if JdbcTemplate is not available (e.g., when database
        // is disabled)
        if (jdbcTemplate == null) {
            log.debug("JdbcTemplate not available, skipping database error logging. Error: {}",
                    errorLogData.get("errorType"));
            // Still log to console for visibility
            log.info("Error log data (database not available): {}", errorLogData);
            return;
        }

        try {
            String sql = "INSERT INTO error_logs (timestamp, error_type, error_message, stack_trace, " +
                    "request_path, request_method, request_body, user_agent, client_ip, severity) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            jdbcTemplate.update(sql,
                    errorLogData.get("timestamp"),
                    errorLogData.get("errorType"),
                    errorLogData.get("errorMessage"),
                    errorLogData.get("stackTrace"),
                    errorLogData.get("requestPath"),
                    errorLogData.get("requestMethod"),
                    errorLogData.get("requestBody"),
                    errorLogData.get("userAgent"),
                    errorLogData.get("clientIp"),
                    errorLogData.get("severity"));

            log.info("Error log saved to database: {}", errorLogData.get("errorType"));
        } catch (Exception e) {
            log.error("Failed to save error log to database", e);
            // Fallback: log to console if database save fails
            log.info("Error log data (database save failed): {}", errorLogData);
        }
    }

    /**
     * Error severity levels
     */
    public enum ErrorSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
