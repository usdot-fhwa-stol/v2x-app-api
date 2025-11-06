package usdot.v2x.app.api.models.etx.configuration.limits;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class LimitDeserializer extends JsonDeserializer<Limit> {

    @Override
    public Limit deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        // Inspect the JSON and decide which subclass to instantiate
        if (node.has("speed")) {
            return p.getCodec().treeToValue(node, LimitSpeedItem.class);
        } else if (node.has("heading")) {
            return p.getCodec().treeToValue(node, LimitHeadingItem.class);
        }

        // Fallback or error if no suitable type is found
        throw new IllegalArgumentException("Unable to deserialize Limit: Unknown structure");
    }
}