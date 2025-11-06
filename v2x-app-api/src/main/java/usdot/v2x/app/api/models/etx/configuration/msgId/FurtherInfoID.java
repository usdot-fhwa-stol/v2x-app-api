package usdot.v2x.app.api.models.etx.configuration.msgId;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
public @Data non-sealed class FurtherInfoID implements MessageId {
    private final String value;
}