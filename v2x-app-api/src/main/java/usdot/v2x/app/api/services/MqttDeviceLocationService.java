package usdot.v2x.app.api.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import usdot.v2x.app.api.models.mqtt.MqttDeviceLocation;
import usdot.v2x.app.api.models.mqtt.MqttDeviceLocationRequest;
import usdot.v2x.app.api.models.mqtt.MqttDeviceLocationResponse;
import usdot.v2x.app.api.repositories.MqttDeviceLocationRepository;
import usdot.v2x.app.api.utils.SecurityContextUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Service for managing MQTT device locations.
 */
@Slf4j
@Service
public class MqttDeviceLocationService {

    private final MqttDeviceLocationRepository repository;
    private final SecurityContextUtils securityContextUtils;
    private final GeohashService geohashService;

    @Autowired
    public MqttDeviceLocationService(
            MqttDeviceLocationRepository repository,
            SecurityContextUtils securityContextUtils,
            GeohashService geohashService) {
        this.repository = repository;
        this.securityContextUtils = securityContextUtils;
        this.geohashService = geohashService;
    }

    /**
     * Register or update MQTT device location.
     */
    @Transactional
    public MqttDeviceLocationResponse registerOrUpdateLocation(MqttDeviceLocationRequest request) {
        String vendorId = securityContextUtils.determineVendorId();
        String requestedBy = securityContextUtils.determineRequestedBy();

        Optional<MqttDeviceLocation> existing = repository.findByMqttClientId(request.getMqttClientId());

        MqttDeviceLocation deviceLocation;
        if (existing.isPresent()) {
            deviceLocation = existing.get();
            deviceLocation.setLatitude(request.getLatitude());
            deviceLocation.setLongitude(request.getLongitude());
            deviceLocation.setElevation(request.getElevation());
            deviceLocation.setHeading(request.getHeading());
            deviceLocation.setSpeed(request.getSpeed());
            deviceLocation.setLastUpdated(Instant.now());
            deviceLocation.setIsActive(true);
            log.debug("Updating location for MQTT client: {}", request.getMqttClientId());
        } else {
            deviceLocation = new MqttDeviceLocation();
            deviceLocation.setMqttClientId(request.getMqttClientId());
            deviceLocation.setLatitude(request.getLatitude());
            deviceLocation.setLongitude(request.getLongitude());
            deviceLocation.setElevation(request.getElevation());
            deviceLocation.setHeading(request.getHeading());
            deviceLocation.setSpeed(request.getSpeed());
            deviceLocation.setVendorId(vendorId);
            deviceLocation.setRegisteredBy(requestedBy);
            deviceLocation.setIsActive(true);
            deviceLocation.setCreatedAt(Instant.now());
            deviceLocation.setLastUpdated(Instant.now());
            log.debug("Registering new MQTT client location: {}", request.getMqttClientId());
        }

        // Calculate geohash
        String geohash = geohashService.calculateGeohash(request.getLatitude(), request.getLongitude(), 7);
        deviceLocation.setGeohash(geohash);

        MqttDeviceLocation saved = repository.save(deviceLocation);
        log.info("Saved MQTT device location: clientId={}, lat={}, lon={}, geohash={}",
                saved.getMqttClientId(), saved.getLatitude(), saved.getLongitude(), saved.getGeohash());

        return MqttDeviceLocationResponse.builder()
                .mqttClientId(saved.getMqttClientId())
                .latitude(saved.getLatitude())
                .longitude(saved.getLongitude())
                .geohash(saved.getGeohash())
                .isActive(saved.getIsActive())
                .lastUpdated(saved.getLastUpdated())
                .lastConnected(saved.getLastConnected())
                .build();
    }

    /**
     * Get device location by MQTT client ID.
     */
    public Optional<MqttDeviceLocationResponse> getLocation(String mqttClientId) {
        return repository.findByMqttClientId(mqttClientId)
                .map(device -> MqttDeviceLocationResponse.builder()
                        .mqttClientId(device.getMqttClientId())
                        .latitude(device.getLatitude())
                        .longitude(device.getLongitude())
                        .geohash(device.getGeohash())
                        .isActive(device.getIsActive())
                        .lastUpdated(device.getLastUpdated())
                        .lastConnected(device.getLastConnected())
                        .build());
    }

    /**
     * Find MQTT client IDs in the given geohashes.
     */
    public Set<String> findMqttClientIdsInGeohashes(Set<String> geohashes) {
        return repository.findMqttClientIdsByGeohashes(geohashes);
    }

    /**
     * Update last connected timestamp.
     */
    @Transactional
    public void updateLastConnected(String mqttClientId) {
        repository.updateLastConnected(mqttClientId, Instant.now());
    }

    /**
     * Deactivate a device (on disconnect).
     */
    @Transactional
    public void deactivateDevice(String mqttClientId) {
        repository.findByMqttClientId(mqttClientId).ifPresent(device -> {
            device.setIsActive(false);
            device.setLastUpdated(Instant.now());
            repository.save(device);
            log.info("Deactivated MQTT device: {}", mqttClientId);
        });
    }
}



