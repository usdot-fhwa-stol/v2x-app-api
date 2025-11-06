package usdot.v2x.app.api.decode;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.*;

@Hidden
@RestController
@RequestMapping("/api/v2/decode")
public class DecodeRestController {
    DecodeApi decodeApi;

    DecodeRestController(
            DecodeApi decodeApi) {
        this.decodeApi = decodeApi;
    }

    @PostMapping(value = "/hex", consumes = TEXT_PLAIN_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Decode ASN.1 hex message", description = "Decodes an ASN.1 message from hexadecimal format to JER. "
            +
            "This endpoint is used to convert binary ASN.1 messages to human-readable JSON format " +
            "for V2X message analysis and debugging. **Note: This endpoint does not require authentication.**", security = {}, requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ASN.1 message in hexadecimal format", required = true, content = @Content(mediaType = "text/plain", examples = {
                    @ExampleObject(name = "Traveler Information Message (TIM)", summary = "Decode a Traveler Information Message (TIM)", value = "001F66702132188D442EF0020C6B1A010F775D9B0301C26E8EC181F543DA3FFFF93F48990C7080A007799A7BB7F74107963D197920A9CB0E6CDA71A0872694399160000000026E8EC181F543DA3EFFFF29FFFE0A6E8EC181F543DA3ABA007D30000009821EEEBB3600")
            })), responses = {
                    @ApiResponse(responseCode = "200", description = "Message decoded successfully", content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "Decoded Message", summary = "JSON representation of decoded ASN.1 message", value = """
                                    {
                                        "messageFrame": {
                                            "messageId": "basicSafetyMessage",
                                            "value": {
                                                "coreData": {
                                                    "msgCnt": 1,
                                                    "id": "123456789",
                                                    "secMark": 1000,
                                                    "lat": 400000000,
                                                    "long": -800000000,
                                                    "elev": 100,
                                                    "accuracy": {
                                                        "semiMajor": 255,
                                                        "semiMinor": 255,
                                                        "orientation": 65535
                                                    },
                                                    "transmission": "neutral",
                                                    "speed": 500,
                                                    "heading": 28800,
                                                    "angle": 0,
                                                    "accelSet": {
                                                        "long": 0,
                                                        "lat": 0,
                                                        "vert": 0,
                                                        "yaw": 0
                                                    },
                                                    "brakes": {
                                                        "wheelBrakes": {
                                                            "unavailable": false,
                                                            "leftFront": false,
                                                            "leftRear": false,
                                                            "rightFront": false,
                                                            "rightRear": false
                                                        },
                                                        "traction": "unavailable",
                                                        "abs": "unavailable",
                                                        "scs": "unavailable",
                                                        "brakeBoost": "unavailable",
                                                        "auxBrakes": "unavailable"
                                                    },
                                                    "size": {
                                                        "width": 200,
                                                        "length": 500
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    """)
                    }))
            })
    public String hexDecode(
            @Parameter(description = "ASN.1 message in hexadecimal format", required = true, example = "3082010A020100A0810104...") @RequestBody String uperHex)
            throws JsonProcessingException {
        return decodeApi.decode(uperHex);
    }
}
