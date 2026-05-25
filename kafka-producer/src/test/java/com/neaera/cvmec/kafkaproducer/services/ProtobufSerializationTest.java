package com.neaera.cvmec.kafkaproducer.services;

import com.neaera.cvmec.kafkaproducer.models.GeoHashRoutedMsg;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class ProtobufSerializationTest {

    @Test
    public void testProtobufSerialization() {
        // Create a sample Java object
        GeoHashRoutedMsg javaMessage = new GeoHashRoutedMsg();
        javaMessage.setMsgBytes("48656c6c6f20576f726c64"); // "Hello World" in hex
        javaMessage.setGeohash("9q8yy");

        // Convert to protobuf
        Instant instant = Instant.now();
        Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();

        us.dot.its.jpo.ode.mec.deposit.models.etx.mqtt.GeoHashRoutedMsg protobufMessage = us.dot.its.jpo.ode.mec.deposit.models.etx.mqtt.GeoHashRoutedMsg
                .newBuilder()
                .setMsgBytes(ByteString.copyFrom(javaMessage.getMsgBytes()))
                .setTime(timestamp)
                .setGeohash(javaMessage.getGeohash())
                .build();

        // Serialize to byte array
        byte[] serializedData = protobufMessage.toByteArray();

        // Deserialize back
        try {
            us.dot.its.jpo.ode.mec.deposit.models.etx.mqtt.GeoHashRoutedMsg deserializedMessage = us.dot.its.jpo.ode.mec.deposit.models.etx.mqtt.GeoHashRoutedMsg
                    .parseFrom(serializedData);

            // Verify data integrity
            assertEquals(javaMessage.getGeohash(), deserializedMessage.getGeohash());
            assertArrayEquals(javaMessage.getMsgBytes(), deserializedMessage.getMsgBytes().toByteArray());
            assertEquals(timestamp.getSeconds(), deserializedMessage.getTime().getSeconds());
            assertEquals(timestamp.getNanos(), deserializedMessage.getTime().getNanos());

            System.out.println("Protobuf serialization test passed!");
            System.out.println("Serialized size: " + serializedData.length + " bytes");
            System.out.println("Geohash: " + deserializedMessage.getGeohash());

        } catch (Exception e) {
            fail("Failed to deserialize protobuf message: " + e.getMessage());
        }
    }
}