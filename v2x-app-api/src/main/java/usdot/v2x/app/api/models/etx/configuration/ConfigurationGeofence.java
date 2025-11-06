package usdot.v2x.app.api.models.etx.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;
import usdot.v2x.app.api.models.etx.configuration.messages.Message;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties({ "active" })
public @Data class ConfigurationGeofence {
    @JsonProperty("name")
    private String name;
    @JsonProperty("description")
    private String description;
    @JsonProperty("geoFence")
    private GeofenceFeatureCollection geoFence;
    @JsonProperty("messageStandard")
    private MessageStandard messageStandard = MessageStandard.SAE; // Default to SAE
    @JsonProperty("messages")
    private List<Message> messages;
    @JsonProperty("isActive")
    private boolean isActive;
}