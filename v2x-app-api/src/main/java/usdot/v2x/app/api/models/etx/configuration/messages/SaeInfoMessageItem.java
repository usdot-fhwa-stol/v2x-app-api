package usdot.v2x.app.api.models.etx.configuration.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.configuration.content.SaeInfoContent;
import usdot.v2x.app.api.models.etx.configuration.msgId.MessageId;
import lombok.Data;

import java.util.List;

public @Data class SaeInfoMessageItem {
    @JsonProperty("typeEvent")
    private Integer typeEvent;
    @JsonProperty("notUsed")
    private Integer notUsed;
    @JsonProperty("description")
    private List<Integer> description;
    @JsonProperty("msgId")
    private MessageId msgId;
    @JsonProperty("startYear")
    private Integer startYear;
    @JsonProperty("startTime")
    private Integer startTime;
    @JsonProperty("durationTime")
    private Integer durationTime;
    @JsonProperty("priority")
    private Integer priority;
    @JsonProperty("notUsed1")
    private Integer notUsed1;
    @JsonProperty("regions")
    private List<GeographicalPath> regions;
    @JsonProperty("notUsed2")
    private Integer notUsed2;
    @JsonProperty("notUsed3")
    private Integer notUsed3;
    @JsonProperty("notUsed4")
    private Integer notUsed4;
    @JsonProperty("content")
    private SaeInfoContent content;
}
