package com.neaera.cvmec.kafkaproducer.services;

import com.neaera.cvmec.kafkaproducer.config.PostgresProperties;
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

    private final PostgresProperties postgresProperties;

    public PostgresService(PostgresProperties postgresProperties) {
        this.postgresProperties = postgresProperties;
    }

    public List<GeohashPayloadMessage> getGeohashPayloadMessages() {
        TypedQuery<GeohashPayloadMessage> query = entityManager.createQuery(
                postgresProperties.getQuery().getFindGeohashPayloads(),
                GeohashPayloadMessage.class);
        return query.getResultList();
    }
}
