package usdot.v2x.app.api.models.geofence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "message_types")
@Data
public class MessageType {

    @Id
    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "description")
    private String description;

    @Column(name = "asn_class", unique = true)
    private String asnClass;
}
