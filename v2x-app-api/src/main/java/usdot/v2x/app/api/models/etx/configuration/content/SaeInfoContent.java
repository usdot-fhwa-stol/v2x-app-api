package usdot.v2x.app.api.models.etx.configuration.content;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = SaeInfoContentDeserializer.class)
public sealed interface SaeInfoContent
        permits AdvisoryContent, ExitServiceContent, GenericSignContent, SpeedLimitContent, WorkZoneContent {
}
