package com.neaera.cvmec.kafkaproducer.services;

import com.neaera.cvmec.kafkaproducer.config.KafkaTopics;
import com.neaera.cvmec.kafkaproducer.models.GeoHashRoutedMsg;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, byte[]> protobufKafkaTemplate;

    private KafkaTopics kafkaTopics;

    public KafkaProducerService(KafkaTemplate<String, byte[]> protobufKafkaTemplate,
            KafkaTopics kafkaTopics) {
        this.protobufKafkaTemplate = protobufKafkaTemplate;
        this.kafkaTopics = kafkaTopics;
    }

    /**
     * Send GeoHashRoutedMsg as protobuf to Kafka
     */
    public void sendGeoHashRoutedMsgAsProtobuf(GeoHashRoutedMsg message) {
        try {
            // Use current timestamp for accurate publishing time
            Instant instant = Instant.now();
            Timestamp timestamp = Timestamp.newBuilder()
                    .setSeconds(instant.getEpochSecond())
                    .setNanos(instant.getNano())
                    .build();

            // Build protobuf message
            us.dot.its.jpo.ode.mec.deposit.models.etx.mqtt.GeoHashRoutedMsg protobufMessage = us.dot.its.jpo.ode.mec.deposit.models.etx.mqtt.GeoHashRoutedMsg
                    .newBuilder()
                    .setMsgBytes(ByteString.copyFrom(message.getMsgBytes()))
                    .setTime(timestamp)
                    .setGeohash(message.getGeohash())
                    .build();

            // Serialize protobuf to byte array and send to Kafka
            byte[] serializedMessage = protobufMessage.toByteArray();
            UUID uuid = UUID.randomUUID();
            protobufKafkaTemplate.send(kafkaTopics.getGeoHashRoutedMsg(), uuid.toString(), serializedMessage);
        } catch (Exception e) {
            log.error("Failed to serialize and send protobuf message: {}", message, e);
        }
    }
}
