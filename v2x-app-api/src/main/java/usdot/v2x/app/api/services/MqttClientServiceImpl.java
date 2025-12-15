package usdot.v2x.app.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import usdot.v2x.app.api.models.mqtt.MqttClient;
import usdot.v2x.app.api.models.mqtt.MqttClientRegistrationRequest;
import usdot.v2x.app.api.models.mqtt.MqttClientResponse;
import usdot.v2x.app.api.models.mqtt.MqttCertificateResponse;
import usdot.v2x.app.api.repositories.MqttClientRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqttClientServiceImpl implements MqttClientService {
    
    private final MqttClientRepository mqttClientRepository;
    private final PasswordEncoder passwordEncoder;
    private final MqttCertificateService certificateService;
    
    @Value("${mosquitto.certs.directory:/mosquitto/certs}")
    private String certsDirectory;
    
    private static final String ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int DEFAULT_PASSWORD_LENGTH = 32;
    
    @Override
    @Transactional
    public MqttClientResponse registerClient(MqttClientRegistrationRequest request, String createdBy) {
        // Generate client ID if not provided
        String clientId = request.getClientId();
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = "mqtt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            log.debug("Auto-generated client ID: {}", clientId);
        }
        
        log.info("Registering MQTT client: {} for user: {}", clientId, createdBy);
        
        // Check if client already exists
        if (mqttClientRepository.existsByClientId(clientId)) {
            throw new IllegalArgumentException("Client ID already exists: " + clientId);
        }
        
        // Generate username (same as clientId for certificate-based auth)
        String username = clientId;
        
        // Generate a dummy password hash (not used for certificate auth, but required by schema)
        String passwordHash = passwordEncoder.encode(generateRandomPassword());
        
        // Create client entity
        MqttClient client = new MqttClient();
        client.setClientId(clientId);
        client.setUsername(username);
        client.setPasswordHash(passwordHash);
        client.setCreatedBy(createdBy);
        client.setIsActive(true);
        // Manually set timestamps to ensure they're not null
        Instant now = Instant.now();
        client.setCreatedAt(now);
        client.setUpdatedAt(now);
        
        // Generate certificate (required for certificate-based authentication)
        if (Boolean.TRUE.equals(request.getGenerateCertificate())) {
            try {
                MqttCertificateResponse certResponse = certificateService.generateClientCertificate(
                    clientId, certsDirectory);
                client.setCertificateCn(certResponse.getClientId());
                client.setCertificateSerial(certResponse.getSerialNumber());
                client.setCertificateExpiresAt(certResponse.getExpiresAt());
                log.info("Generated certificate for client: {}", clientId);
            } catch (Exception e) {
                log.error("Failed to generate certificate for client: {}", clientId, e);
                throw new RuntimeException("Certificate generation is required but failed: " + e.getMessage(), e);
            }
        } else {
            throw new IllegalArgumentException("Certificate generation is required for certificate-based authentication");
        }
        
        MqttClient savedClient = mqttClientRepository.save(client);
        
        log.info("Successfully registered MQTT client: {}", clientId);
        return mapToResponse(savedClient);
    }
    
    @Override
    public MqttClientResponse getClient(String clientId) {
        log.debug("Retrieving MQTT client: {}", clientId);
        return mqttClientRepository.findByClientId(clientId)
                .map(this::mapToResponse)
                .orElse(null);
    }
    
    @Override
    public List<MqttClientResponse> getClientsByUser(String createdBy) {
        log.debug("Retrieving MQTT clients for user: {}", createdBy);
        return mqttClientRepository.findByCreatedBy(createdBy)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public boolean deleteClient(String clientId) {
        log.info("Deleting MQTT client: {}", clientId);
        
        Optional<MqttClient> clientOpt = mqttClientRepository.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return false;
        }
        
        MqttClient client = clientOpt.get();
        client.setIsActive(false);
        mqttClientRepository.save(client);
        
        log.info("Successfully deactivated MQTT client: {}", clientId);
        return true;
    }
    
    @Override
    public MqttCertificateResponse getClientCertificate(String clientId) {
        log.debug("Retrieving certificate for client: {}", clientId);
        
        MqttClient client = mqttClientRepository.findByClientId(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));
        
        if (client.getCertificateCn() == null) {
            throw new IllegalStateException("No certificate found for client: " + clientId);
        }
        
        return certificateService.getClientCertificate(clientId, certsDirectory);
    }
    
    @Override
    @Transactional
    public void updateLastConnected(String clientId) {
        log.debug("Updating last connected timestamp for client: {}", clientId);
        mqttClientRepository.findByClientId(clientId).ifPresent(client -> {
            client.setLastConnectedAt(Instant.now());
            mqttClientRepository.save(client);
        });
    }
    
    private MqttClientResponse mapToResponse(MqttClient client) {
        return MqttClientResponse.builder()
                .clientId(client.getClientId())
                .username(client.getUsername())
                .isActive(client.getIsActive())
                .certificateCn(client.getCertificateCn())
                .certificateExpiresAt(client.getCertificateExpiresAt())
                .lastConnectedAt(client.getLastConnectedAt())
                .createdAt(client.getCreatedAt())
                .build();
    }
    
    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(DEFAULT_PASSWORD_LENGTH);
        for (int i = 0; i < DEFAULT_PASSWORD_LENGTH; i++) {
            password.append(ALLOWED_CHARS.charAt(random.nextInt(ALLOWED_CHARS.length())));
        }
        return password.toString();
    }
}

