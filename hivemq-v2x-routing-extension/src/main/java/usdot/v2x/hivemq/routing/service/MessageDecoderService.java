package usdot.v2x.hivemq.routing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import j2735ffm.MessageFrameCodec;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.asn.j2735.r2024.BasicSafetyMessage.BasicSafetyMessage;
import us.dot.its.jpo.asn.j2735.r2024.Common.BSMcoreData;
import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

/**
 * Service for decoding ASN.1 J2735 messages and extracting location data
 * Supports both JSON-encoded and ASN.1 UPER binary messages
 */
@Slf4j
public class MessageDecoderService {

    private final ObjectMapper jsonMapper;
    private final XmlMapper xmlMapper;
    private final MessageFrameCodec codec;
    private static final long TEXT_BUFFER_SIZE = 262144L; // 256KB
    private static final long UPER_BUFFER_SIZE = 8192L; // 8KB
    private static final long ERROR_BUFFER_SIZE = 256L;

    public MessageDecoderService() {
        this.jsonMapper = new ObjectMapper();
        this.xmlMapper = new XmlMapper();
        this.codec = initializeCodec();
    }

    /**
     * Initialize MessageFrameCodec with library path resolution
     */
    private MessageFrameCodec initializeCodec() {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            String libraryName = isWindows ? "asnapplication.dll" : "libasnapplication.so";

            // Try to load from classpath resources first
            String resourcePath = "j2735ffm/" + libraryName;
            URL resourceUrl = getClass().getClassLoader().getResource(resourcePath);

            Path libPath = null;
            if (resourceUrl != null) {
                try {
                    // If the resource is in a JAR file, we need to extract it to a temp file
                    if (resourceUrl.getProtocol().equals("jar")) {
                        // Extract library from JAR to temporary file
                        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                            if (is != null) {
                                File tempFile = File.createTempFile("libasnapplication", ".so");
                                tempFile.deleteOnExit();

                                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                                    is.transferTo(fos);
                                }

                                // Make executable
                                tempFile.setExecutable(true, false);
                                libPath = tempFile.toPath();
                                log.info("Extracted ASN.1 library from JAR to temporary file: {}", libPath);
                            }
                        }
                    } else {
                        // Resource is a regular file
                        libPath = Paths.get(resourceUrl.toURI());
                        if (Files.exists(libPath)) {
                            log.info("Using ASN.1 library from classpath: {}", libPath);
                        } else {
                            libPath = null;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Could not load classpath library: {}", e.getMessage());
                }
            }

            // Try common paths if classpath resource not found
            if (libPath == null || !java.nio.file.Files.exists(libPath)) {
                String[] commonPaths = {
                        "/usr/lib/libasnapplication.so",
                        System.getProperty("user.dir") + "/j2735-ffm-java/j2735-2024-ffm-lib/lib/" + libraryName,
                        System.getProperty("user.dir") + "/lib/" + libraryName
                };

                for (String path : commonPaths) {
                    Path testPath = Paths.get(path);
                    if (java.nio.file.Files.exists(testPath)) {
                        libPath = testPath;
                        log.info("Using ASN.1 library from: {}", libPath);
                        break;
                    }
                }
            }

            if (libPath == null || !java.nio.file.Files.exists(libPath)) {
                log.warn("ASN.1 native library not found. Binary decoding will be disabled.");
                log.warn("Library should be at: {}", libraryName);
                return null; // Codec will be null, only JSON decoding will work
            }

            return new MessageFrameCodec(
                    TEXT_BUFFER_SIZE,
                    UPER_BUFFER_SIZE,
                    ERROR_BUFFER_SIZE,
                    libPath);
        } catch (Exception e) {
            log.error("Failed to initialize MessageFrameCodec: {}", e.getMessage(), e);
            return null; // Fall back to JSON-only decoding
        }
    }

    /**
     * Decode BSM message and extract location data
     * 
     * @param payload Message payload (ASN.1 UPER binary or JSON)
     * @return LocationData if location can be extracted, null otherwise
     */
    public LocationData decodeAndExtractBSMLocation(ByteBuffer payload) {
        if (payload == null || !payload.hasRemaining()) {
            return null;
        }

        try {
            // Try JSON first (if message is already decoded)
            if (isJsonPayload(payload)) {
                return extractBSMLocationFromJson(payload);
            }

            // Try ASN.1 UPER binary decoding
            if (codec != null) {
                return extractBSMLocationFromAsn1Binary(payload);
            } else {
                log.debug("ASN.1 codec not available, cannot decode binary payload");
                return null;
            }

        } catch (Exception e) {
            log.error("Error decoding BSM: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Check if payload is JSON
     */
    private boolean isJsonPayload(ByteBuffer payload) {
        payload.mark();
        try {
            byte[] bytes = new byte[Math.min(payload.remaining(), 100)];
            payload.get(bytes);
            String preview = new String(bytes, StandardCharsets.UTF_8).trim();
            return preview.startsWith("{") || preview.startsWith("[");
        } finally {
            payload.reset();
        }
    }

    /**
     * Extract location from ASN.1 UPER binary BSM message
     */
    private LocationData extractBSMLocationFromAsn1Binary(ByteBuffer payload) {
        try {
            // Convert ByteBuffer to byte array
            byte[] bytes = new byte[payload.remaining()];
            payload.duplicate().get(bytes);

            // Convert UPER to XER
            String xer = codec.uperToXer(bytes);

            // Deserialize XER to MessageFrame
            MessageFrame<?> messageFrame = xmlMapper.readValue(xer, MessageFrame.class);

            // Extract location from BSM
            Object value = messageFrame.getValue();
            if (value == null || !(value instanceof BasicSafetyMessage)) {
                log.debug("MessageFrame does not contain BasicSafetyMessage");
                return null;
            }

            return extractBSMLocationFromPojo((BasicSafetyMessage) value);

        } catch (Exception e) {
            log.debug("Error decoding ASN.1 binary BSM: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract location from BSM POJO
     */
    private LocationData extractBSMLocationFromPojo(BasicSafetyMessage bsm) {
        if (bsm == null || bsm.getCoreData() == null) {
            return null;
        }
        BSMcoreData coreData = bsm.getCoreData();
        if (coreData.getLat() == null || coreData.getLong_() == null) {
            return null;
        }
        // J2735 uses microdegrees
        double latitude = coreData.getLat().getValue() * 1e-6;
        double longitude = coreData.getLong_().getValue() * 1e-6;
        return new LocationData(latitude, longitude);
    }

    /**
     * Extract location from JSON-encoded BSM message
     */
    private LocationData extractBSMLocationFromJson(ByteBuffer payload) {
        try {
            byte[] bytes = new byte[payload.remaining()];
            payload.duplicate().get(bytes);
            String jsonStr = new String(bytes, StandardCharsets.UTF_8);

            JsonNode root = jsonMapper.readTree(jsonStr);
            JsonNode message = root;

            // Navigate to BSM message content
            if (root.has("value")) {
                JsonNode value = root.get("value");
                if (value.has("BasicSafetyMessage")) {
                    message = value.get("BasicSafetyMessage");
                } else {
                    message = value;
                }
            }

            // Extract from coreData
            JsonNode coreData = message.path("coreData");
            if (coreData.isMissingNode()) {
                return null;
            }
            JsonNode lat = coreData.path("lat");
            JsonNode lon = coreData.path("long");
            if (lat.isMissingNode() || lon.isMissingNode()) {
                return null;
            }
            // J2735 uses microdegrees
            double latitude = lat.asDouble() * 1e-6;
            double longitude = lon.asDouble() * 1e-6;
            return new LocationData(latitude, longitude);
        } catch (Exception e) {
            log.debug("Error extracting location from JSON BSM: {}", e.getMessage());
            return null;
        }
    }

    public void cleanup() {
        // Cleanup resources if needed
    }
}
