package usdot.v2x.georouter.config;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Jackson XML serialization/deserialization.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public XmlMapper xmlMapper() {
        return new XmlMapper();
    }
}
