package usdot.v2x.app.api.models.etx.configuration.limits;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

@Data
@JsonDeserialize(using = LimitDeserializer.class)
public non-sealed class LimitHeadingItem implements Limit {
    @JsonProperty("description")
    private String description;
    @JsonProperty("heading")
    private LimitItem heading;
}
