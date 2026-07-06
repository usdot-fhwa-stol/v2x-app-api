package com.neaera.cvmec.kafkaproducer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "kafka-producer.postgres")
@Data
public class PostgresProperties {

    private Query query = new Query();

    @Data
    public static class Query {
        private String findGeohashPayloads;
    }
}
