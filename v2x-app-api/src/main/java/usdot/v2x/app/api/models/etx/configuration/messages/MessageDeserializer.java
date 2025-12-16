package usdot.v2x.app.api.models.etx.configuration.messages;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import usdot.v2x.app.api.models.etx.configuration.geofence.DistributionSchedule;
import usdot.v2x.app.api.models.etx.configuration.geofence.DistributionType;
import usdot.v2x.app.api.models.etx.configuration.geofence.RoadUserType;
import usdot.v2x.app.api.models.etx.configuration.geofence.TriggerCondition;
import usdot.v2x.app.api.models.etx.configuration.limits.Limit;

import java.io.IOException;
import java.util.List;

public class MessageDeserializer extends JsonDeserializer<Message> {

    @Override
    public Message deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        ObjectMapper mapper = new ObjectMapper();
        // Inspect the JSON and decide which subclass to instantiate
        if (node.has("generic")) {
            GenericMessage genericMessage = new GenericMessage();
            genericMessage.setPrivate(node.get("isPrivate").asBoolean());
            genericMessage.setRoadUserType(mapper.convertValue(node.get("roadUserType"),
                    new TypeReference<List<RoadUserType>>() {
                    }));
            genericMessage.setTriggerConditions(mapper.convertValue(node.get("triggerConditions"),
                    new TypeReference<List<TriggerCondition>>() {
                    }));
            genericMessage.setLimits(mapper.convertValue(node.get("limits"),
                    new TypeReference<List<Limit>>() {
                    }));
            genericMessage.setDistributionType(mapper.convertValue(node.get("distributionType"),
                    new TypeReference<List<DistributionType>>() {
                    }));
            genericMessage.setDistributionSchedule(
                    p.getCodec().treeToValue(node.get("distributionSchedule"),
                            DistributionSchedule.class));
            genericMessage.setGeneric(
                    p.getCodec().treeToValue(node.get("generic"), GenericMessageItem.class));
            return genericMessage;
        } else if (node.has("saeAlert")) {
            SaeAlertMessage saeAlertMessage = new SaeAlertMessage();
            saeAlertMessage.setPrivate(node.get("isPrivate").asBoolean());
            saeAlertMessage.setRoadUserType(mapper.convertValue(node.get("roadUserType"),
                    new TypeReference<List<RoadUserType>>() {
                    }));
            saeAlertMessage.setTriggerConditions(mapper.convertValue(node.get("triggerConditions"),
                    new TypeReference<List<TriggerCondition>>() {
                    }));
            saeAlertMessage.setLimits(mapper.convertValue(node.get("limits"),
                    new TypeReference<List<Limit>>() {
                    }));
            saeAlertMessage.setDistributionType(mapper.convertValue(node.get("distributionType"),
                    new TypeReference<List<DistributionType>>() {
                    }));
            saeAlertMessage.setDistributionSchedule(
                    p.getCodec().treeToValue(node.get("distributionSchedule"),
                            DistributionSchedule.class));
            saeAlertMessage.setSaeAlert(
                    p.getCodec().treeToValue(node.get("saeAlert"), SaeAlertMessageItem.class));
        } else if (node.has("saeInfo")) {
            SaeInfoMessage saeInfoMessage = new SaeInfoMessage();
            saeInfoMessage.setPrivate(node.get("isPrivate").asBoolean());
            saeInfoMessage.setRoadUserType(mapper.convertValue(node.get("roadUserType"),
                    new TypeReference<List<RoadUserType>>() {
                    }));
            saeInfoMessage.setTriggerConditions(mapper.convertValue(node.get("triggerConditions"),
                    new TypeReference<List<TriggerCondition>>() {
                    }));
            saeInfoMessage.setLimits(mapper.convertValue(node.get("limits"),
                    new TypeReference<List<Limit>>() {
                    }));
            saeInfoMessage.setDistributionType(mapper.convertValue(node.get("distributionType"),
                    new TypeReference<List<DistributionType>>() {
                    }));
            saeInfoMessage.setDistributionSchedule(
                    p.getCodec().treeToValue(node.get("distributionSchedule"),
                            DistributionSchedule.class));
            saeInfoMessage.setSaeInfo(
                    p.getCodec().treeToValue(node.get("saeInfo"), SaeInfoMessageItem.class));
        } else if (node.has("etsiAlert")) {
            EtsiAlertMessage etsiAlertMessage = new EtsiAlertMessage();
            etsiAlertMessage.setPrivate(node.get("isPrivate").asBoolean());
            etsiAlertMessage.setRoadUserType(mapper.convertValue(node.get("roadUserType"),
                    new TypeReference<List<RoadUserType>>() {
                    }));
            etsiAlertMessage.setTriggerConditions(mapper.convertValue(node.get("triggerConditions"),
                    new TypeReference<List<TriggerCondition>>() {
                    }));
            etsiAlertMessage.setLimits(mapper.convertValue(node.get("limits"),
                    new TypeReference<List<Limit>>() {
                    }));
            etsiAlertMessage.setDistributionType(mapper.convertValue(node.get("distributionType"),
                    new TypeReference<List<DistributionType>>() {
                    }));
            etsiAlertMessage.setDistributionSchedule(p.getCodec()
                    .treeToValue(node.get("distributionSchedule"), DistributionSchedule.class));
            etsiAlertMessage.setEtsiAlert(
                    p.getCodec().treeToValue(node.get("etsiAlert"), EtsiAlertMessageItem.class));
        }
        // Fallback or error if no suitable type is found
        throw new IllegalArgumentException("Unable to deserialize Message: Unknown structure");
    }
}