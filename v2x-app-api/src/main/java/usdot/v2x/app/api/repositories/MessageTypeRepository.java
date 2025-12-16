package usdot.v2x.app.api.repositories;

import usdot.v2x.app.api.models.geofence.MessageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MessageTypeRepository extends JpaRepository<MessageType, String> {
    Optional<MessageType> findByAsnClass(String asnClass);
}
