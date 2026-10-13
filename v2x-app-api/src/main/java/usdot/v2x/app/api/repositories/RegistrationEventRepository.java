package usdot.v2x.app.api.repositories;

import usdot.v2x.app.api.models.etx.registration.RegistrationEvent;
import usdot.v2x.app.api.models.etx.registration.RegistrationEventType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RegistrationEventRepository extends JpaRepository<RegistrationEvent, Long> {

    /**
     * Find events in a time window, optionally filtered by device and/or vendor
     */
    @Query("SELECT e FROM RegistrationEvent e WHERE e.createdAt >= :from AND e.createdAt <= :to "
            + "AND (:deviceId IS NULL OR e.deviceId = :deviceId) "
            + "AND (:vendorId IS NULL OR e.vendorId = :vendorId) "
            + "ORDER BY e.createdAt DESC")
    List<RegistrationEvent> findEvents(@Param("deviceId") String deviceId,
            @Param("vendorId") String vendorId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    boolean existsByDeviceIdAndEventType(String deviceId, RegistrationEventType eventType);
}
