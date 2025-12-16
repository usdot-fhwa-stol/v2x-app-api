package usdot.v2x.app.api.models.etx.configuration.content;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class SaeInfoContentDeserializer extends JsonDeserializer<SaeInfoContent> {

    @Override
    public SaeInfoContent deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        // Inspect the JSON and decide which subclass to instantiate
        if (node.has("advisory")) {
            return p.getCodec().treeToValue(node, AdvisoryContent.class);
        } else if (node.has("exitService")) {
            return p.getCodec().treeToValue(node, ExitServiceContent.class);
        } else if (node.has("genericSign")) {
            return p.getCodec().treeToValue(node, GenericSignContent.class);
        } else if (node.has("speedLimit")) {
            return p.getCodec().treeToValue(node, SpeedLimitContent.class);
        } else if (node.has("workZone")) {
            return p.getCodec().treeToValue(node, WorkZoneContent.class);
        }

        // Fallback or error if no suitable type is found
        throw new IllegalArgumentException("Unable to deserialize SaeInfoContent: Unknown structure");
    }
}