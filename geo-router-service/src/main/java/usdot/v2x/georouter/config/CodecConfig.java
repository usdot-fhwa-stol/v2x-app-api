package usdot.v2x.georouter.config;

import j2735ffm.MessageFrameCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for J2735 MessageFrameCodec.
 */
@Slf4j
@Configuration
public class CodecConfig {

    @Value("${j2735.codec.text-buffer-size:262144}")
    private long textBufferSize;

    @Value("${j2735.codec.uper-buffer-size:8192}")
    private long uperBufferSize;

    @Value("${j2735.codec.error-buffer-size:256}")
    private long errorBufferSize;

    @Value("${j2735.codec.library-path:/usr/lib/libasnapplication.so}")
    private String libraryPath;

    @Value("${j2735.codec.windows-library-path:C:/temp/asnapplication.dll}")
    private String windowsLibraryPath;

    @Bean
    public MessageFrameCodec messageFrameCodec() {
        Path libPath;
        String osName = System.getProperty("os.name").toLowerCase();
        
        if (osName.contains("win")) {
            libPath = Paths.get(windowsLibraryPath);
        } else {
            libPath = Paths.get(libraryPath);
        }

        log.info("Initializing MessageFrameCodec with library path: {}", libPath);
        return new MessageFrameCodec(textBufferSize, uperBufferSize, errorBufferSize, libPath);
    }
}


