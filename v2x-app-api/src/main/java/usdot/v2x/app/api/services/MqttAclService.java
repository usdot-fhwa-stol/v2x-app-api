package usdot.v2x.app.api.services;

import usdot.v2x.app.api.models.mqtt.MqttAclRequest;
import usdot.v2x.app.api.models.mqtt.MqttAclResponse;

import java.util.List;

public interface MqttAclService {
    /**
     * Create or update an ACL entry
     * 
     * @param request ACL request
     * @param createdBy Username of the user creating the ACL
     * @return ACL response
     */
    MqttAclResponse createOrUpdateAcl(MqttAclRequest request, String createdBy);
    
    /**
     * Get all ACL entries for a client
     * 
     * @param clientId Client ID
     * @return List of ACL responses
     */
    List<MqttAclResponse> getAclEntries(String clientId);
    
    /**
     * Delete an ACL entry
     * 
     * @param aclId ACL entry ID
     * @return True if deleted, false if not found
     */
    boolean deleteAclEntry(Long aclId);
    
    /**
     * Regenerate the Mosquitto ACL file from database entries
     * 
     * @return True if successful
     */
    boolean regenerateAclFile();
}

