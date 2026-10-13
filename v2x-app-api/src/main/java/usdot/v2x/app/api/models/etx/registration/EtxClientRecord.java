package usdot.v2x.app.api.models.etx.registration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * A single registered client as returned by the ETX
 * {@code POST /api/v1/clients/query} endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public @Data class EtxClientRecord {
    @JsonProperty("DeviceID")
    private String deviceId;
    @JsonProperty("ClientType")
    private RegistrationClientType clientType;
    @JsonProperty("ClientSubtype")
    private RegistrationClientSubType clientSubtype;
    @JsonProperty("MecIds")
    private List<String> mecIds;
}
