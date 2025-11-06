package usdot.v2x.app.api.models.etx.configuration.geofence;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class DistributionSchedule {
    @JsonProperty("repeatPeriod")
    private Integer repeatPeriod;
    @JsonProperty("duration")
    private Integer duration;
    @JsonProperty("startTime")
    private String startTime;
}
