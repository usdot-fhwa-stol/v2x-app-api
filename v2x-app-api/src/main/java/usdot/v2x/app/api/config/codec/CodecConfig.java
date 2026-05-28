package usdot.v2x.app.api.config.codec;

import j2735ffm.MessageFrameCodec;
import lombok.extern.slf4j.Slf4j;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for J2735 ASN.1 codec setup.
 */
@Slf4j
@Configuration
public class CodecConfig {

    private final CodecProperties config;

    public CodecConfig(CodecProperties config) {
        this.config = config;
    }

    @Bean
    @ConditionalOnProperty(name = "j2735.codec.enabled", havingValue = "true", matchIfMissing = true)
    public MessageFrameCodec messageFrameCodec() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String configuredPath = isWindows
                ? config.getWindowsLibraryPath()
                : config.getLibraryPath();

        Path libPath = resolveLibraryPath(configuredPath, isWindows);

        try {
            return new MessageFrameCodec(
                    config.getTextBufferSize(),
                    config.getUperBufferSize(),
                    config.getErrorBufferSize(),
                    libPath);
        } catch (RuntimeException e) {
            // Check if it's a GLIBC version mismatch
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("Cannot open library")) {
                log.error("Failed to load library due to compatibility issue.");
                log.error("Library path: {}", libPath);
                log.error("This is likely a GLIBC version mismatch.");
                log.error("The library was built with a newer GLIBC than your system supports.");
                log.error("Solutions:");
                log.error(
                        "1. Rebuild the library locally using: cd api/j2735-2024-ffm-lib && docker compose -f docker-compose-build.yml up --build");
                log.error("2. Or copy a compatible library to one of the search paths listed above.");
                log.error("3. Check library compatibility with: ldd {}", libPath);
            }
            throw e;
        }
    }

    private Path resolveLibraryPath(String configuredPath, boolean isWindows) {
        String libraryName = isWindows ? "asnapplication.dll" : "libasnapplication.so";

        // First, try to load from classpath resources (for local development)
        String resourcePath = "j2735ffm/" + libraryName;
        URL resourceUrl = getClass().getClassLoader().getResource(resourcePath);
        if (resourceUrl != null) {
            try {
                Path classpathPath = Paths.get(resourceUrl.toURI());
                if (java.nio.file.Files.exists(classpathPath)) {
                    log.info("Using library from classpath: {}", classpathPath);
                    return classpathPath;
                }
            } catch (URISyntaxException e) {
                log.warn("Could not resolve classpath library path: {}", e.getMessage());
            }
        }

        // Try common local development paths FIRST (prioritize over configured path for
        // local dev)
        // These are more likely to have the correct architecture for the developer's
        // machine
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");
        String[] commonPaths = {
                // Check relative to current working directory (most likely for local dev)
                currentDir + "/j2735-2024-ffm-lib/j2735-2024-ffm-lib/src/test/resources/j2735ffm/" + libraryName,
                currentDir + "/api/j2735-2024-ffm-lib/j2735-2024-ffm-lib/src/test/resources/j2735ffm/" + libraryName,
                currentDir + "/j2735-2024-ffm-lib/lib/" + libraryName,
                currentDir + "/api/j2735-2024-ffm-lib/lib/" + libraryName,
                // Check relative paths from typical working directories
                "./j2735-2024-ffm-lib/j2735-2024-ffm-lib/src/test/resources/j2735ffm/" + libraryName,
                "../j2735-2024-ffm-lib/j2735-2024-ffm-lib/src/test/resources/j2735ffm/" + libraryName,
                "../../j2735-2024-ffm-lib/j2735-2024-ffm-lib/src/test/resources/j2735ffm/" + libraryName,
                "./j2735-2024-ffm-lib/lib/" + libraryName,
                "../j2735-2024-ffm-lib/lib/" + libraryName,
                // User home directory
                userHome + "/.local/lib/" + libraryName,
                "./lib/" + libraryName
        };

        for (String commonPath : commonPaths) {
            Path testPath = Paths.get(commonPath).toAbsolutePath().normalize();
            if (java.nio.file.Files.exists(testPath)) {
                log.info("Using library from local development path: {}", testPath);
                return testPath;
            }
        }

        // Finally, check configured path (for Docker/production)
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new IllegalStateException(
                    "j2735.codec.library-path is not configured and the library was not found in any common path. "
                            + "Set j2735.codec.library-path in application.yml or ensure application.yml is on the classpath.");
        }
        Path configuredLibPath = Paths.get(configuredPath);
        if (java.nio.file.Files.exists(configuredLibPath)) {
            log.info("Using configured library path: {}", configuredPath);
            return configuredLibPath;
        }

        // If none found, log warning with helpful suggestions
        log.warn("Library not found. Searched classpath and common paths.");
        log.warn("For local development, ensure the x86 library is available at one of:");
        for (String commonPath : commonPaths) {
            log.warn("  - {}", Paths.get(commonPath).toAbsolutePath().normalize());
        }
        log.warn("Configured path: {}", configuredPath);

        return configuredLibPath;
    }

}
