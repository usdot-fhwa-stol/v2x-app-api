package usdot.v2x.app.api.etx.configuration;

import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.n52.jackson.datatype.jts.JtsModule;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import usdot.v2x.app.api.models.etx.configuration.ConfigurationGeofenceResponse;

public class ConfigurationGeofenceResponseTest {
    @Test
    public void testSerializationDeserialization() throws IOException, JSONException {
        // Load sample JSON data
        String expectedJson = new String(
                Files.readAllBytes(
                        Paths.get(
                                "src/test/resources/com/neaera/cvmec/api/etx/configuration/ConfigurationResponse.json")));
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        objectMapper.registerModule(new JtsModule());

        ConfigurationGeofenceResponse geofence = objectMapper.readValue(expectedJson,
                ConfigurationGeofenceResponse.class);
        String resultJson = objectMapper.writeValueAsString(geofence);

        assertThat(resultJson, jsonEquals(expectedJson).withTolerance(0.0001));
    }
}
