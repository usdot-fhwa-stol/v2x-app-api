package com.neaera.cvmec.kafkaproducer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Kafka topics.
 */
@Configuration
@ConfigurationProperties(prefix = "kafka-producer.kafka.topics")
@Data
public class KafkaTopics {
    private String geoHashRoutedMsg;
}
