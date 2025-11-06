package usdot.v2x.app.api.etx.registration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;

import com.fasterxml.jackson.databind.ObjectMapper;
import usdot.v2x.app.api.models.etx.registration.ConnectionPostRequest;

public class ConnectionPostRequestTest {
    @Test
    public void testSerializationDeserialization() throws IOException, JSONException {
        // Load sample JSON data
        String expectedJson = new String(
                Files.readAllBytes(
                        Paths.get(
                                "src/test/resources/usdot/v2x/app/api/etx/registration/ConnectionPostRequest.json")));
        ObjectMapper objectMapper = new ObjectMapper();
        // JavaTimeModule

        ConnectionPostRequest geofence = objectMapper.readValue(expectedJson, ConnectionPostRequest.class);
        String resultJson = objectMapper.writeValueAsString(geofence);

        // Compare JSON with ignored fields
        JSONAssert.assertEquals(expectedJson, resultJson, new CustomComparator(
                JSONCompareMode.LENIENT));
    }
}
