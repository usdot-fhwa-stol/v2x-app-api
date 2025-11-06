package usdot.v2x.app.api.models.etx.configuration.content.itis;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class ItisItemDeserializer extends JsonDeserializer<ItisItem> {

    @Override
    public ItisItem deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        // Inspect the JSON and decide which subclass to instantiate
        if (node.has("itis")) {
            return p.getCodec().treeToValue(node, ItisItemItis.class);
        } else if (node.has("text")) {
            return p.getCodec().treeToValue(node, ItisItemText.class);
        }

        // Fallback or error if no suitable type is found
        throw new IllegalArgumentException("Unable to deserialize ItisItem: Unknown structure");
    }
}