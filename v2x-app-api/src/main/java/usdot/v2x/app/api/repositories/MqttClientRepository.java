package usdot.v2x.app.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import usdot.v2x.app.api.models.mqtt.MqttClient;

import java.util.List;
import java.util.Optional;

@Repository
public interface MqttClientRepository extends JpaRepository<MqttClient, Long> {
    Optional<MqttClient> findByClientId(String clientId);
    
    List<MqttClient> findByCreatedBy(String createdBy);
    
    List<MqttClient> findByCreatedByAndIsActive(String createdBy, Boolean isActive);
    
    @Query("SELECT m FROM MqttClient m WHERE m.isActive = true")
    List<MqttClient> findAllActive();
    
    boolean existsByClientId(String clientId);
    
    @Query("SELECT COUNT(m) FROM MqttClient m WHERE m.createdBy = :createdBy AND m.isActive = true")
    long countByCreatedByAndIsActive(@Param("createdBy") String createdBy);
}

