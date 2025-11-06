package usdot.v2x.app.api.models.etx.configuration.messages;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.configuration.messages.etsi_alert.DenmPayload;
import usdot.v2x.app.api.models.etx.configuration.messages.etsi_alert.Management;

public @Data class EtsiAlertMessageItem {
    @JsonProperty("management")
    private Management management;
    @JsonProperty("denm")
    private DenmPayload denm;
}
