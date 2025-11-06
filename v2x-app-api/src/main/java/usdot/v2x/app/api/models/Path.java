package usdot.v2x.app.api.models;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "paths")
@Data
@EntityListeners(AuditingEntityListener.class)
public class Path {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type", nullable = false)
    private String type = "Feature";

    @Column(name = "geometry_type", nullable = false)
    private String geometryType = "LineString";

    @ElementCollection
    @CollectionTable(name = "path_coordinates", joinColumns = @JoinColumn(name = "path_id"))
    @Column(name = "coordinate")
    private List<String> coordinates;

    @ElementCollection
    @CollectionTable(name = "path_timestamps", joinColumns = @JoinColumn(name = "path_id"))
    @Column(name = "timestamp")
    private List<Long> timestamps;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;
}
