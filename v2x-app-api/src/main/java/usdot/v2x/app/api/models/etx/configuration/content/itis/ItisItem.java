package usdot.v2x.app.api.models.etx.configuration.content.itis;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = ItisItemDeserializer.class)
public sealed interface ItisItem permits ItisItemItis, ItisItemText {
}
