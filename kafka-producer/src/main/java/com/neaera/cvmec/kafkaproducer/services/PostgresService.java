package com.neaera.cvmec.kafkaproducer.services;

import com.neaera.cvmec.kafkaproducer.models.postgres.derived.GeohashPayloadMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PostgresService {

    @PersistenceContext
    private EntityManager entityManager;

    private final String findGeohashPayloads = "SELECT new com.neaera.cvmec.kafkaproducer.models.postgres.derived.GeohashPayloadMessage("
            + "gg.geohash, gd.hexPayload) "
            + "FROM GeofenceGeohashes gg "
            + "JOIN GeofenceDeployments gd ON gg.geofenceDeploymentId = gd.id "
            + "WHERE gd.isActive = true";

    public List<GeohashPayloadMessage> getGeohashPayloadMessages() {
        TypedQuery<GeohashPayloadMessage> query = entityManager.createQuery(findGeohashPayloads,
                GeohashPayloadMessage.class);
        return query.getResultList();
    }
}
