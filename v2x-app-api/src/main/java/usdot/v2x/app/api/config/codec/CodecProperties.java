package usdot.v2x.app.api.config.codec;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "j2735.codec")
@Data
public class CodecProperties {
    long textBufferSize;
    long uperBufferSize;
    long errorBufferSize;
    String libraryPath;
    String windowsLibraryPath;
}