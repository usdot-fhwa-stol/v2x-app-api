package usdot.v2x.app.api.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import j2735ffm.MessageFrameCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;

import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Utility class for calculating TIM expiration times from J2735 message
 * content.
 * This class extracts startTime, durationTime, and startYear from TIM messages
 * to determine when the TIM should expire.
 */
@Component
@Slf4j
@ConditionalOnBean(MessageFrameCodec.class)
public class TimExpirationCalculator {

    private final XmlMapper xmlMapper;
    private final MessageFrameCodec codec;

    public TimExpirationCalculator(@Qualifier("xmlMapper") XmlMapper xmlMapper, MessageFrameCodec codec) {
        this.xmlMapper = xmlMapper;
        this.codec = codec;
    }

    /**
     * Calculate the expiration time for a TIM message based on its J2735 content.
     * 
     * @param hexPayload       The ASN.1 hex payload of the TIM message
     * @param gracePeriodHours Additional grace period in hours after calculated
     *                         expiration
     * @return The calculated expiration time, or null if calculation fails
     */
    public Instant calculateExpirationTime(String hexPayload, int gracePeriodHours) {
        try {
            MessageFrame<?> messageFrame = parseMessageFrame(hexPayload);

            if (messageFrame instanceof TravelerInformationMessageFrame) {
                TravelerInformationMessageFrame timFrame = (TravelerInformationMessageFrame) messageFrame;
                TravelerInformation tim = timFrame.getValue();

                return calculateExpirationFromTim(tim, gracePeriodHours);
            } else {
                log.warn("Message is not a TIM message, cannot calculate expiration time");
                return null;
            }
        } catch (Exception e) {
            log.error("Error calculating expiration time from TIM message: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Calculate expiration time from a decoded TIM message.
     * 
     * @param tim              The decoded TravelerInformation message
     * @param gracePeriodHours Additional grace period in hours after calculated
     *                         expiration
     * @return The calculated expiration time, or null if calculation fails
     */
    private Instant calculateExpirationFromTim(TravelerInformation tim, int gracePeriodHours) {
        try {
            List<TravelerDataFrame> dataFrames = tim.getDataFrames();

            if (dataFrames.isEmpty()) {
                log.warn("No data frames found in TIM message");
                return null;
            }

            // Use the first data frame for expiration calculation
            TravelerDataFrame dataFrame = dataFrames.get(0);

            // Extract timing information
            long startYear = dataFrame.getStartYear().getValue();
            long startTimeMinutes = dataFrame.getStartTime().getValue();
            long durationTime = dataFrame.getDurationTime().getValue();

            // Calculate the expiration time using utility method
            Instant expiration = TimExpirationUtils.calculateExpirationTime(
                    (int) startYear, startTimeMinutes, durationTime, gracePeriodHours);

            log.debug("Calculated TIM expiration: startYear={}, startTimeMinutes={}, durationTime={}, " +
                    "expirationTime={} (with {}h grace period)",
                    startYear, startTimeMinutes, durationTime, expiration, gracePeriodHours);

            return expiration;

        } catch (Exception e) {
            log.error("Error calculating expiration from TIM data frame: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Check if a TIM message is currently valid based on its timing information.
     * 
     * @param hexPayload The ASN.1 hex payload of the TIM message
     * @return True if the TIM is currently valid, false otherwise
     */
    public boolean isTimCurrentlyValid(String hexPayload) {
        try {
            MessageFrame<?> messageFrame = parseMessageFrame(hexPayload);

            if (messageFrame instanceof TravelerInformationMessageFrame) {
                TravelerInformationMessageFrame timFrame = (TravelerInformationMessageFrame) messageFrame;
                TravelerInformation tim = timFrame.getValue();

                return isTimCurrentlyValid(tim);
            } else {
                log.warn("Message is not a TIM message, cannot determine validity");
                return false;
            }
        } catch (Exception e) {
            log.error("Error checking TIM validity: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if a decoded TIM message is currently valid.
     * 
     * @param tim The decoded TravelerInformation message
     * @return True if the TIM is currently valid, false otherwise
     */
    private boolean isTimCurrentlyValid(TravelerInformation tim) {
        try {
            List<TravelerDataFrame> dataFrames = tim.getDataFrames();

            if (dataFrames.isEmpty()) {
                return false;
            }

            TravelerDataFrame dataFrame = dataFrames.get(0);
            long startYear = dataFrame.getStartYear().getValue();
            long startTimeMinutes = dataFrame.getStartTime().getValue();
            long durationTime = dataFrame.getDurationTime().getValue();

            return TimExpirationUtils.isTimCurrentlyValid((int) startYear, startTimeMinutes, durationTime);

        } catch (Exception e) {
            log.error("Error checking TIM validity: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Parse ASN.1 hex string to MessageFrame.
     * 
     * @param asn1Hex The ASN.1 hex string
     * @return The parsed MessageFrame
     * @throws JsonProcessingException If parsing fails
     */
    private MessageFrame<?> parseMessageFrame(String asn1Hex) throws JsonProcessingException {
        try {
            // Trim the hex string to remove any headers and get just the message payload
            String trimmedHex = UperUtil.trimToMessagePayload(asn1Hex);
            byte[] bytes = HexFormat.of().parseHex(trimmedHex);
            String xer = codec.uperToXer(bytes);
            return xmlMapper.readValue(xer, MessageFrame.class);
        } catch (Exception e) {
            log.error("Failed to parse ASN1 hex", e);
            throw new RuntimeException("Failed to parse ASN1 hex: " + e.getMessage(), e);
        }
    }
}
