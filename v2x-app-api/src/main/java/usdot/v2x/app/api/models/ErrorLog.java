package usdot.v2x.app.api.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "error_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "error_type", nullable = false)
    private String errorType;

    @Column(name = "error_message", nullable = false, length = 1000)
    private String errorMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "request_path")
    private String requestPath;

    @Column(name = "request_method")
    private String requestMethod;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "client_ip")
    private String clientIp;

    @Column(name = "severity", nullable = false)
    @Enumerated(EnumType.STRING)
    private ErrorSeverity severity;

    public enum ErrorSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
