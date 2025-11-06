package usdot.v2x.app.api.decode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.etx.ErrorResponseException;

import j2735ffm.MessageFrameCodec;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DecodeApi {

    private final MessageFrameCodec codec;
    private final XmlMapper xmlMapper;
    private final ObjectMapper objectMapper;

    public DecodeApi(
            MessageFrameCodec codec,
            @Qualifier("xmlMapper") XmlMapper xmlMapper,
            ObjectMapper objectMapper) {
        this.codec = codec;
        this.xmlMapper = xmlMapper;
        this.objectMapper = objectMapper;
    }

    public String decode(String uperHex) throws JsonProcessingException {
        try {
            byte[] bytes = HexFormat.of().parseHex(uperHex);
            String xer = codec.uperToXer(bytes);
            MessageFrame<?> messageFrame = xmlMapper.readValue(xer, MessageFrame.class);
            return objectMapper.writeValueAsString(messageFrame);
        } catch (Exception e) {
            log.error("Failed to decode message: {}", e.getMessage(), e);
            throw new ErrorResponseException(
                    new ErrorResponse("Failed to decode message", e.getMessage()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}
