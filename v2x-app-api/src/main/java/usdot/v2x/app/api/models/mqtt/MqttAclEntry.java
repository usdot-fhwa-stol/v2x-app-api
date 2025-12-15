package usdot.v2x.app.api.models.mqtt;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "mqtt_acl_entries")
@Data
@EntityListeners(AuditingEntityListener.class)
public class MqttAclEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "topic_pattern", nullable = false, length = 512)
    private String topicPattern;

    @Column(name = "access_type", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private AccessType accessType;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public enum AccessType {
        read, write, readwrite
    }
}

