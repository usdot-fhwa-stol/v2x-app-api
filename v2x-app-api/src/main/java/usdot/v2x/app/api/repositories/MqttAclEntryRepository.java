package usdot.v2x.app.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import usdot.v2x.app.api.models.mqtt.MqttAclEntry;

import java.util.List;
import java.util.Optional;

@Repository
public interface MqttAclEntryRepository extends JpaRepository<MqttAclEntry, Long> {
    List<MqttAclEntry> findByClientId(String clientId);
    
    List<MqttAclEntry> findByClientIdAndIsActive(String clientId, Boolean isActive);
    
    Optional<MqttAclEntry> findByClientIdAndTopicPatternAndAccessType(
            String clientId, String topicPattern, MqttAclEntry.AccessType accessType);
    
    @Query("SELECT m FROM MqttAclEntry m WHERE m.isActive = true")
    List<MqttAclEntry> findAllActive();
    
    @Modifying
    @Query("UPDATE MqttAclEntry m SET m.isActive = false WHERE m.clientId = :clientId")
    void deactivateByClientId(@Param("clientId") String clientId);
}

