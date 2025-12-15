package usdot.v2x.app.api.services;

import usdot.v2x.app.api.models.mqtt.MqttClientRegistrationRequest;
import usdot.v2x.app.api.models.mqtt.MqttClientResponse;
import usdot.v2x.app.api.models.mqtt.MqttCertificateResponse;

import java.util.List;

public interface MqttClientService {
    /**
     * Register a new MQTT client
     * 
     * @param request Registration request
     * @param createdBy Username of the user creating the client
     * @return Client response with generated password if not provided
     */
    MqttClientResponse registerClient(MqttClientRegistrationRequest request, String createdBy);
    
    /**
     * Get a client by client ID
     * 
     * @param clientId Client ID
     * @return Client response or null if not found
     */
    MqttClientResponse getClient(String clientId);
    
    /**
     * Get all clients created by a user
     * 
     * @param createdBy Username
     * @return List of client responses
     */
    List<MqttClientResponse> getClientsByUser(String createdBy);
    
    /**
     * Delete a client
     * 
     * @param clientId Client ID
     * @return True if deleted, false if not found
     */
    boolean deleteClient(String clientId);
    
    /**
     * Generate or retrieve client certificate
     * 
     * @param clientId Client ID
     * @return Certificate response with PEM-encoded certificates
     */
    MqttCertificateResponse getClientCertificate(String clientId);
    
    /**
     * Update last connected timestamp
     * 
     * @param clientId Client ID
     */
    void updateLastConnected(String clientId);
}

