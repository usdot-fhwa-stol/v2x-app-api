package usdot.v2x.app.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import usdot.v2x.app.api.models.geofence.TimConfigurationResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
public class TimConfigurationServiceImpl implements TimConfigurationService {

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${tim.config.file.path:/tim_config_files/tim-config.json}")
    private String timConfigFilePath;

    @Value("${tim.icons.directory:/tim_config_files/tim-icons}")
    private String timIconsDirectory;

    @Override
    public Mono<TimConfigurationResponse> getTimConfiguration() {
        return Mono.fromCallable(() -> {
            try {
                Path configPath = Paths.get(timConfigFilePath);
                if (!Files.exists(configPath)) {
                    throw new RuntimeException("TIM configuration file not found at: " + timConfigFilePath);
                }

                String jsonContent = Files.readString(configPath);
                return objectMapper.readValue(jsonContent, TimConfigurationResponse.class);
            } catch (IOException e) {
                log.error("Error reading TIM configuration file", e);
                throw new RuntimeException("Failed to read TIM configuration", e);
            }
        });
    }

    @Override
    public Mono<byte[]> getTimIconsTarGz(String version) {
        return Mono.fromCallable(() -> {
            try {
                return createTarGzFromVersionedIcons(version);
            } catch (IOException e) {
                log.error("Error creating TIM icons tar.gz file for version: " + version, e);
                throw new RuntimeException("Failed to create TIM icons tar.gz file for version: " + version, e);
            }
        });
    }

    private byte[] createTarGzFromVersionedIcons(String version) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Path versionedIconsDir = Paths.get(timIconsDirectory, "v" + version);

        if (!Files.exists(versionedIconsDir)) {
            log.warn("Versioned icons directory does not exist: " + versionedIconsDir);
            // Return empty tar.gz
            return createEmptyTarGz();
        }

        try (GzipCompressorOutputStream gzipOut = new GzipCompressorOutputStream(baos);
                TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {

            // Set long file mode to handle long filenames
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            // Add all files from the versioned icons directory
            addFilesToTarGz(versionedIconsDir, versionedIconsDir, tarOut);
        }

        return baos.toByteArray();
    }

    private void addFilesToTarGz(Path rootDir, Path source, TarArchiveOutputStream tarOut) throws IOException {
        Files.walk(source)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        String entryName = rootDir.relativize(file).toString();
                        TarArchiveEntry entry = new TarArchiveEntry(file.toFile(), entryName);
                        tarOut.putArchiveEntry(entry);

                        // Copy file content to tar
                        Files.copy(file, tarOut);
                        tarOut.closeArchiveEntry();

                        log.debug("Added file to tar.gz: " + entryName);
                    } catch (IOException e) {
                        log.error("Error adding file to archive: " + file, e);
                    }
                });
    }

    private byte[] createEmptyTarGz() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gzipOut = new GzipCompressorOutputStream(baos);
                TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {
            // Create an empty tar.gz file - just opening and closing the streams is enough
            tarOut.finish();
        }
        return baos.toByteArray();
    }

}
