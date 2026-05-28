package com.neaera.cvmec.kafkaproducer.models.postgres.derived;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@EqualsAndHashCode
@ToString
@AllArgsConstructor
public class GeohashPayloadMessage {
    private String geohash;
    private String hexPayload;
}
