package usdot.v2x.app.api.utils;

import org.apache.tomcat.util.buf.HexUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.DSRCmsgID;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UperUtil Tests")
class UperUtilTest {

    @Test
    @DisplayName("Constructor should throw UnsupportedOperationException")
    void testConstructor() {
        assertThrows(UnsupportedOperationException.class, () -> {
            // Use reflection to access private constructor
            Constructor<UperUtil> constructor = UperUtil.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            try {
                constructor.newInstance();
            } catch (InvocationTargetException e) {
                // Unwrap the actual exception
                throw (UnsupportedOperationException) e.getCause();
            }
        });
    }

    @Test
    @DisplayName("stripDot2Header should strip header when start flag is found")
    void testStripDot2Header_Success() {
        String hexString = "1234567890abcdef";
        String payloadStartFlag = "abcd";

        String result = UperUtil.stripDot2Header(hexString, payloadStartFlag);

        assertEquals("abcdef", result);
    }

    @Test
    @DisplayName("stripDot2Header should handle case insensitive search")
    void testStripDot2Header_CaseInsensitive() {
        String hexString = "1234567890ABCDEF";
        String payloadStartFlag = "abcd";

        String result = UperUtil.stripDot2Header(hexString, payloadStartFlag);

        assertEquals("abcdef", result);
    }

    @Test
    @DisplayName("stripDot2Header should throw exception when start flag not found")
    void testStripDot2Header_StartFlagNotFound() {
        String hexString = "1234567890abcdef";
        String payloadStartFlag = "xyz";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            UperUtil.stripDot2Header(hexString, payloadStartFlag);
        });

        assertTrue(exception.getMessage().contains("Start flag 'xyz' not found"));
    }

    @Test
    @DisplayName("stripDot2Header should handle empty hex string")
    void testStripDot2Header_EmptyHexString() {
        String hexString = "";
        String payloadStartFlag = "abcd";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            UperUtil.stripDot2Header(hexString, payloadStartFlag);
        });

        assertTrue(exception.getMessage().contains("Start flag 'abcd' not found"));
    }

    @Test
    @DisplayName("stripDot3Header with byte array should strip headers")
    void testStripDot3Header_ByteArray() {
        String hexString = "1234567890abcdef";
        byte[] packet = HexUtils.fromHexString(hexString);
        HashMap<String, String> msgStartFlags = new HashMap<>();
        msgStartFlags.put("TEST", "abcd");

        byte[] result = UperUtil.stripDot3Header(packet, msgStartFlags);

        String resultHex = HexUtils.toHexString(result);
        assertEquals("abcdef", resultHex);
    }

    @Test
    @DisplayName("stripDot3Header with byte array should handle signed 1609.2 header")
    void testStripDot3Header_ByteArray_WithSignedHeader() {
        String hexString = "1234038100abcdef";
        byte[] packet = HexUtils.fromHexString(hexString);
        HashMap<String, String> msgStartFlags = new HashMap<>();
        msgStartFlags.put("TEST", "abcd");

        byte[] result = UperUtil.stripDot3Header(packet, msgStartFlags);

        String resultHex = HexUtils.toHexString(result);
        assertEquals("038100abcdef", resultHex);
    }

    @Test
    @DisplayName("stripDot3Header with byte array should return original when no start flag found")
    void testStripDot3Header_ByteArray_NoStartFlag() {
        String hexString = "1234567890abcdef";
        byte[] packet = HexUtils.fromHexString(hexString);
        HashMap<String, String> msgStartFlags = new HashMap<>();
        msgStartFlags.put("TEST", "xyz");

        byte[] result = UperUtil.stripDot3Header(packet, msgStartFlags);

        String resultHex = HexUtils.toHexString(result);
        assertEquals(hexString, resultHex);
    }

    @Test
    @DisplayName("stripDot3Header with string should strip headers")
    void testStripDot3Header_String() {
        String hexString = "1234567890abcdef";
        String payloadStartFlag = "abcd";

        String result = UperUtil.stripDot3Header(hexString, payloadStartFlag);

        assertEquals("abcdef", result);
    }

    @Test
    @DisplayName("stripDot3Header with string should handle signed 1609.2 header")
    void testStripDot3Header_String_WithSignedHeader() {
        String hexString = "1234038100abcdef";
        String payloadStartFlag = "abcd";

        String result = UperUtil.stripDot3Header(hexString, payloadStartFlag);

        assertEquals("038100abcdef", result);
    }

    @ParameterizedTest
    @CsvSource({
            "0014, BASICSAFETYMESSAGE",
            "0012, MAPDATA",
            "0013, SIGNALPHASEANDTIMINGMESSAGE",
            "001f, TRAVELERINFORMATION",
            "0020, PERSONALSAFETYMESSAGE",
            "001d, SIGNALREQUESTMESSAGE",
            "001e, SIGNALSTATUSMESSAGE",
            "0029, SENSORDATASHARINGMESSAGE",
            "001c, RTCMCORRECTIONS",
            "0021, ROADSAFETYMESSAGE"
    })
    @DisplayName("determineHexPacketType should detect message types correctly")
    void testDetermineHexPacketType_MessageTypes(String hexMsgId, String expectedType) {
        String hexString = "123456" + hexMsgId + "abcdef";

        String result = UperUtil.determineHexPacketType(hexString);

        assertEquals(expectedType, result);
    }

    @Test
    @DisplayName("determineHexPacketType should return empty string when no message type found")
    void testDetermineHexPacketType_NoMessageType() {
        String hexString = "1234567890abcdef";

        String result = UperUtil.determineHexPacketType(hexString);

        assertEquals("", result);
    }

    @Test
    @DisplayName("determineHexPacketType should handle empty string")
    void testDetermineHexPacketType_EmptyString() {
        String result = UperUtil.determineHexPacketType("");

        assertEquals("", result);
    }

    @Test
    @DisplayName("determineHexPacketType should handle null string")
    void testDetermineHexPacketType_NullString() {
        String result = UperUtil.determineHexPacketType(null);

        assertEquals("", result);
    }

    @Test
    @DisplayName("determineHexPacketType should handle case insensitive search")
    void testDetermineHexPacketType_CaseInsensitive() {
        String hexString = "1234560014ABCDEF"; // BSM message ID

        String result = UperUtil.determineHexPacketType(hexString);

        assertEquals("BASICSAFETYMESSAGE", result);
    }

    @Test
    @DisplayName("trimToMessagePayload should return original string when no message ID found")
    void testTrimToMessagePayload_NoMessageId() {
        String hexString = "1234567890abcdef";

        String result = UperUtil.trimToMessagePayload(hexString);

        assertEquals(hexString, result);
    }

    @Test
    @DisplayName("trimToMessagePayload should handle null input")
    void testTrimToMessagePayload_NullInput() {
        String result = UperUtil.trimToMessagePayload(null);

        assertNull(result);
    }

    @Test
    @DisplayName("trimToMessagePayload should handle empty input")
    void testTrimToMessagePayload_EmptyInput() {
        String result = UperUtil.trimToMessagePayload("");

        assertEquals("", result);
    }

    @Test
    @DisplayName("findValidStartFlagLocation should find flag at beginning")
    void testFindValidStartFlagLocation_AtBeginning() {
        String hexString = "abcd1234567890";
        String startFlag = "abcd";

        int result = UperUtil.findValidStartFlagLocation(hexString, startFlag);

        assertEquals(0, result);
    }

    @Test
    @DisplayName("findValidStartFlagLocation should find flag after header")
    void testFindValidStartFlagLocation_AfterHeader() {
        String hexString = "1234abcd567890";
        String startFlag = "abcd";

        int result = UperUtil.findValidStartFlagLocation(hexString, startFlag);

        assertEquals(4, result);
    }

    @Test
    @DisplayName("findValidStartFlagLocation should find flag on even byte boundary")
    void testFindValidStartFlagLocation_EvenByteBoundary() {
        String hexString = "1234abcd67890"; // "abcd" at position 4 (even byte boundary)
        String startFlag = "abcd";

        int result = UperUtil.findValidStartFlagLocation(hexString, startFlag);

        // The logic searches from position 4, so it should find "abcd" at position 4
        // which is on an even byte boundary
        assertEquals(4, result);
    }

    @Test
    @DisplayName("findValidStartFlagLocation should skip odd byte boundaries")
    void testFindValidStartFlagLocation_SkipOddBoundaries() {
        String hexString = "1234abcd5678abcd90";
        String startFlag = "abcd";

        int result = UperUtil.findValidStartFlagLocation(hexString, startFlag);

        assertEquals(4, result); // Should find the first occurrence at even boundary
    }

    @Test
    @DisplayName("findValidStartFlagLocation should return -1 when flag not found")
    void testFindValidStartFlagLocation_NotFound() {
        String hexString = "1234567890abcdef";
        String startFlag = "xyz";

        int result = UperUtil.findValidStartFlagLocation(hexString, startFlag);

        assertEquals(-1, result);
    }

    @Test
    @DisplayName("findValidStartFlagLocation should handle empty hex string")
    void testFindValidStartFlagLocation_EmptyHexString() {
        String hexString = "";
        String startFlag = "abcd";

        int result = UperUtil.findValidStartFlagLocation(hexString, startFlag);

        assertEquals(-1, result);
    }

    @Test
    @DisplayName("findValidStartFlagLocation should handle empty start flag")
    void testFindValidStartFlagLocation_EmptyStartFlag() {
        String hexString = "1234567890abcdef";
        String startFlag = "";

        int result = UperUtil.findValidStartFlagLocation(hexString, startFlag);

        assertEquals(0, result); // Empty string is found at index 0
    }

    @Test
    @DisplayName("Secret test: complete workflow with MAP message")
    void testSecret_MAPMessage() {
        // Get the actual MAP message ID from DSRCmsgID class
        Optional<DSRCmsgID> mapOpt = DSRCmsgID.named("mapData");
        assertTrue(mapOpt.isPresent());
        String mapHexId = String.format("%04x", mapOpt.get().getValue());

        // Simulate a MAP message with headers
        String originalHex = "1234567890" + mapHexId + "ghijkl"; // MAP message ID at position 10
        String expectedTrimmed = mapHexId + "ghijkl"; // The method includes the "0" before the message ID

        // Test trimToMessagePayload
        String trimmed = UperUtil.trimToMessagePayload(originalHex);
        assertEquals(expectedTrimmed, trimmed);

        // Test determineHexPacketType - use the original message ID without the extra
        // "0"
        String messageType = UperUtil.determineHexPacketType(mapHexId + "ghijkl");
        assertEquals("MAPDATA", messageType);

        // Test stripDot2Header - this should find the start flag at position 10
        String stripped = UperUtil.stripDot2Header(originalHex, mapHexId);
        assertEquals(mapHexId + "ghijkl", stripped);
    }

    @Test
    @DisplayName("Test with real DSRC message IDs from the class")
    void testWithRealDSRCMessageIds() {
        // Get some real message IDs from DSRCmsgID class
        Optional<DSRCmsgID> timOpt = DSRCmsgID.named("travelerInformation");
        Optional<DSRCmsgID> mapOpt = DSRCmsgID.named("mapData");

        assertTrue(timOpt.isPresent());
        assertTrue(mapOpt.isPresent());

        String timHexId = String.format("%04x", timOpt.get().getValue());
        String mapHexId = String.format("%04x", mapOpt.get().getValue());

        String hexString = "123456" + timHexId + "abcdef";
        String messageType = UperUtil.determineHexPacketType(hexString);
        assertEquals("TRAVELERINFORMATION", messageType);

        String trimmed = UperUtil.trimToMessagePayload(hexString);
        assertEquals(timHexId + "abcdef", trimmed);

        hexString = "123456" + mapHexId + "abcdef";
        messageType = UperUtil.determineHexPacketType(hexString);
        assertEquals("MAPDATA", messageType);

        trimmed = UperUtil.trimToMessagePayload(hexString);
        assertEquals(mapHexId + "abcdef", trimmed);
    }

    @Test
    @DisplayName("Test error handling in determineHexPacketType")
    void testDetermineHexPacketType_ErrorHandling() {
        // This test verifies that exceptions in the DSRCmsgID processing are caught
        // and don't propagate up
        String hexString = "1234567890abcdef";

        // Should not throw exception even if there are issues with DSRCmsgID processing
        assertDoesNotThrow(() -> {
            String result = UperUtil.determineHexPacketType(hexString);
            // Result should be empty string when no message type is found
            assertEquals("", result);
        });
    }
}
