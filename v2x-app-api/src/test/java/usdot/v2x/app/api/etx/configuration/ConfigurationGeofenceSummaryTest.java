package usdot.v2x.app.api.etx.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;

import com.fasterxml.jackson.databind.ObjectMapper;
import usdot.v2x.app.api.models.etx.configuration.ConfigurationGeofenceSummary;

public class ConfigurationGeofenceSummaryTest {
    @Test
    public void testSerializationDeserialization() throws IOException, JSONException {
        // Load sample JSON data
        String expectedJson = new String(
                Files.readAllBytes(
                        Paths.get(
                                "src/test/resources/com/neaera/cvmec/api/etx/configuration/ConfigurationSummary.json")));
        ObjectMapper objectMapper = new ObjectMapper();
        // JavaTimeModule

        ConfigurationGeofenceSummary geofence = objectMapper.readValue(expectedJson,
                ConfigurationGeofenceSummary.class);
        String resultJson = objectMapper.writeValueAsString(geofence);

        // Compare JSON with ignored fields
        JSONAssert.assertEquals(expectedJson, resultJson, new CustomComparator(
                JSONCompareMode.LENIENT));
    }
}
