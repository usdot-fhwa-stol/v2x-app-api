package usdot.v2x.app.api.utils;

import java.util.HashMap;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.buf.HexUtils;
import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.DSRCmsgID;

/**
 * Utility class for handling and manipulating hexadecimal strings representing
 * network packet data,
 * particularly those adhering to IEEE 1609.2 and 1609.3 standards.
 */
@Slf4j
public class UperUtil {

    private UperUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * Strips the IEEE 1609.2 security header (if it exists) and returns the payload
     * from a given
     * hexadecimal string. The method searches for a specified start flag that
     * indicates the beginning
     * of the payload.
     *
     * @param hexString        the input hexadecimal string from which the IEEE
     *                         1609.2 security header needs
     *                         to be stripped.
     * @param payloadStartFlag the start flag indicating the beginning of the
     *                         payload.
     * @return a string representing the payload without the IEEE 1609.2 security
     *         header, if the start
     *         flag is found.
     * @throws IllegalArgumentException if the specified start flag is not found
     *                                  within the
     *                                  hexadecimal string.
     */
    public static String stripDot2Header(String hexString, String payloadStartFlag) {
        hexString = hexString.toLowerCase();
        int startIndex = findValidStartFlagLocation(hexString, payloadStartFlag);
        if (startIndex == -1) {
            throw new IllegalArgumentException(
                    "Start flag '%s' not found in message: '%s'".formatted(payloadStartFlag, hexString));
        }
        return hexString.substring(startIndex);
    }

    /**
     * Strips the 1609.3 and unsigned 1609.2 headers if they are present. Will
     * return the payload with
     * a signed 1609.2 header if it is present. Otherwise, returns just the payload.
     */
    public static byte[] stripDot3Header(byte[] packet, HashMap<String, String> msgStartFlags) {

        String hexString = HexUtils.toHexString(packet);
        String hexPacketParsed = "";

        for (String startFlag : msgStartFlags.values()) {
            int payloadStartIndex = findValidStartFlagLocation(hexString, startFlag);
            if (payloadStartIndex == -1) {
                continue;
            }

            String headers = hexString.substring(0, payloadStartIndex);
            String payload = hexString.substring(payloadStartIndex);

            // Look for the index of the start flag of a signed 1609.2 header, if one exists
            int signedDot2StartIndex = headers.indexOf("038100");
            if (signedDot2StartIndex == -1) {
                hexPacketParsed = payload;
            } else {
                hexPacketParsed = headers.substring(signedDot2StartIndex) + payload;
            }
            break;
        }

        if (hexPacketParsed.isEmpty()) {
            hexPacketParsed = hexString;
            log.debug("Packet is not a recognized message type: {}", hexPacketParsed);
        }
        return HexUtils.fromHexString(hexPacketParsed);
    }

    /**
     * Strips the 1609.3 and unsigned 1609.2 headers if they are present. Will
     * return the payload with
     * a signed 1609.2 header if it is present. Otherwise, returns just the payload.
     */
    public static String stripDot3Header(String hexString, String payloadStartFlag) {
        int payloadStartIndex = findValidStartFlagLocation(hexString, payloadStartFlag);
        String headers = hexString.substring(0, payloadStartIndex);
        String payload = hexString.substring(payloadStartIndex);
        log.debug("Base payload: {}", payload);
        // Look for the index of the start flag of a signed 1609.2 header
        int signedDot2StartIndex = headers.indexOf("038100");
        if (signedDot2StartIndex == -1) {
            return payload;
        } else {
            return headers.substring(signedDot2StartIndex) + payload;
        }
    }

    /**
     * Determines the type of hex packet based on DSRC message ID values from the
     * DSRCmsgID class.
     * This method looks for the message ID in the UPER encoded payload and maps it
     * to a message type.
     *
     * @param hexString the hexadecimal string representing a packet whose type is
     *                  to be determined
     * @return a string indicating the type of the packet, such as "MAP", "SPAT",
     *         "TIM", "BSM", "SSM",
     *         "PSM", "SRM", "SDSM", "RTCM", or "RSM". If no valid type is found,
     *         returns an empty string.
     */
    public static String determineHexPacketType(String hexString) {
        String messageType = "";

        try {
            // Check for each message ID in the hex string
            for (String name : DSRCmsgID.names()) {
                Optional<DSRCmsgID> msgIdOpt = DSRCmsgID.named(name);
                if (msgIdOpt.isPresent()) {
                    DSRCmsgID msgId = msgIdOpt.get();
                    // Convert to 2-byte hex representation (4 hex characters)
                    String hexMsgId = String.format("%04x", msgId.getValue());

                    if (hexString.contains(hexMsgId)) {
                        messageType = name.toUpperCase();
                        log.debug("Detected message type: {} (ID: {}) for hex string: {}",
                                messageType, hexMsgId, hexString);
                        break;
                    }
                }
            }

            if (messageType.isEmpty()) {
                log.debug("No recognized message type found in hex string: {}", hexString);
            }

        } catch (Exception e) {
            log.error("Error determining message type from hex string: {}", hexString, e);
        }

        return messageType;
    }

    /**
     * Trims everything before the message payload start in a UPER encoded hex
     * string.
     * This method identifies the start of the actual message payload and returns
     * only that portion.
     *
     * @param hexString the UPER encoded hexadecimal string
     * @return the trimmed hex string containing only the message payload
     */
    public static String trimToMessagePayload(String hexString) {
        if (hexString == null || hexString.isEmpty()) {
            return hexString;
        }

        hexString = hexString.toLowerCase();

        int earliestIndex = Integer.MAX_VALUE;

        // supported DSRC message names:
        String[] supportedMessageNames = {
                "travelerInformation",
                "mapData",
        };

        // Look for the earliest occurrence of any valid message ID
        for (String name : supportedMessageNames) {
            Optional<DSRCmsgID> msgIdOpt = DSRCmsgID.named(name);
            if (msgIdOpt.isPresent()) {
                DSRCmsgID msgId = msgIdOpt.get();
                // Convert to 2-byte hex representation (4 hex characters)
                String hexMsgId = String.format("%04x", msgId.getValue());
                int index = hexString.indexOf(hexMsgId);

                if (index != -1 && index < earliestIndex) {
                    earliestIndex = index;
                }
            }
        }

        if (earliestIndex != Integer.MAX_VALUE) {
            // Found a message ID, trim to that point
            String trimmed = hexString.substring(earliestIndex);
            log.debug("Trimmed hex string from index {}: {}", earliestIndex, trimmed);
            return trimmed;
        }

        // If no message ID found, return the original string
        log.debug("No message ID found, returning original hex string");
        return hexString;
    }

    /**
     * Searches for the location of the given start flag in the provided hex string
     * and ensures it is
     * on an even numbered byte. If the start flag is found at the beginning of the
     * string or not
     * found at all, it returns immediately. Otherwise, it continues searching from
     * the fifth
     * position. The method ensures that the found start flag is located on an even
     * byte boundary.
     *
     * @param hexString the string representation of the message in hexadecimal
     *                  format where the
     *                  search for the start flag will be conducted.
     * @param startFlag the specific flag pattern to locate within the given hex
     *                  string, indicating
     *                  the start of a valid message.
     * @return the index of the start flag within the hex string if found, and
     *         located on an even byte
     *         boundary; -1 if not found.
     */
    public static int findValidStartFlagLocation(String hexString, String startFlag) {
        int index = hexString.indexOf(startFlag);

        // If the message has a header, make sure not to misidentify the message by the
        // header
        if (index == 0 || index == -1) {
            return index;
        } else {
            index = hexString.indexOf(startFlag, 4);
        }

        // Make sure start flag is on an even numbered byte
        while (index != -1 && index % 2 != 0) {
            index = hexString.indexOf(startFlag, index + 1);
        }
        return index;
    }
}
