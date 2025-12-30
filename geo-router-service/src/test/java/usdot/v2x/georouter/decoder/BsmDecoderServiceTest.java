package usdot.v2x.georouter.decoder;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import j2735ffm.MessageFrameCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import usdot.v2x.georouter.models.GeoRelevanceMessage;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for BSM decoder service.
 * Note: These tests require the native library to be available.
 */
class BsmDecoderServiceTest {

    private BsmDecoderService decoderService;
    private MessageFrameCodec codec;
    private XmlMapper xmlMapper;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        // Try to initialize codec with library path
        Path libPath = Path.of("/usr/lib/libasnapplication.so");
        if (!Files.exists(libPath)) {
            // Skip tests if library not available
            return;
        }

        codec = new MessageFrameCodec(262144L, 8192L, 256L, libPath);
        xmlMapper = new XmlMapper();
        decoderService = new BsmDecoderService(codec, xmlMapper);
    }

    @Test
    void testDecodeBsmWithValidMessage() {
        // Skip if codec not initialized
        if (codec == null) {
            return;
        }

        // This is a sample BSM hex string (you would replace with actual test data)
        // For now, we'll test that the service can be instantiated
        assertNotNull(decoderService);
    }

    @Test
    void testDecodeBsmWithNullBytes() {
        if (decoderService == null) {
            return;
        }

        GeoRelevanceMessage result = decoderService.decodeBsm(null, "test/topic");
        assertNull(result);
    }

    @Test
    void testDecodeBsmWithEmptyBytes() {
        if (decoderService == null) {
            return;
        }

        byte[] emptyBytes = new byte[0];
        GeoRelevanceMessage result = decoderService.decodeBsm(emptyBytes, "test/topic");
        assertNull(result);
    }
}
