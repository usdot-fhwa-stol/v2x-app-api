package usdot.v2x.app.api.models.etx.configuration.messages;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import usdot.v2x.app.api.models.etx.configuration.geofence.DistributionSchedule;
import usdot.v2x.app.api.models.etx.configuration.geofence.DistributionType;
import usdot.v2x.app.api.models.etx.configuration.geofence.RoadUserType;
import usdot.v2x.app.api.models.etx.configuration.geofence.TriggerCondition;
import usdot.v2x.app.api.models.etx.configuration.limits.Limit;

import java.util.List;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
@JsonDeserialize(using = MessageDeserializer.class)
public abstract class Message {
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean privateField;

    @JsonGetter("isPrivate")
    public boolean isPrivate() {
        return privateField;
    }

    @JsonSetter("isPrivate")
    public void setPrivate(boolean privateField) {
        this.privateField = privateField;
    }

    @JsonProperty("roadUserType")
    private List<RoadUserType> roadUserType;
    @JsonProperty("triggerConditions")
    private List<TriggerCondition> triggerConditions;
    @JsonProperty("limits")
    private List<Limit> limits;
    @JsonProperty("distributionType")
    private List<DistributionType> distributionType;
    @JsonProperty("distributionSchedule")
    private DistributionSchedule distributionSchedule;
}
