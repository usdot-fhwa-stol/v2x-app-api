package usdot.v2x.app.api.services;

import usdot.v2x.app.api.models.geofence.GeofenceDeploymentRequest;
import usdot.v2x.app.api.models.geofence.GeofenceDeploymentResponse;

import java.util.List;

public interface GeofenceDeploymentService {

    /**
     * Create a new Geofence deployment
     * 
     * @param request The Geofence deployment request
     * @return The created Geofence deployment response
     */
    GeofenceDeploymentResponse createGeofenceDeployment(GeofenceDeploymentRequest request);

    /**
     * Get a Geofence deployment by Geofence ID
     * 
     * @param geofenceId The Geofence ID
     * @return The Geofence deployment response or null if not found
     */
    GeofenceDeploymentResponse getGeofenceDeployment(String geofenceId);

    /**
     * Get all Geofence deployments by geohash
     * 
     * @param geohash The geohash to search for
     * @return List of Geofence deployment responses
     */
    List<GeofenceDeploymentResponse> getGeofenceDeploymentsByGeohash(String geohash);

    /**
     * Get all geohashes for a Geofence deployment
     * 
     * @param geofenceId The Geofence ID
     * @return List of geohashes
     */
    List<String> getGeofenceGeohashes(String geofenceId);

    /**
     * Deactivate a Geofence deployment
     * 
     * @param geofenceId The Geofence ID to deactivate
     * @return True if successfully deactivated, false if not found
     */
    boolean deactivateGeofenceDeployment(String geofenceId);

    /**
     * Delete a Geofence deployment
     * 
     * @param geofenceId The Geofence ID to delete
     * @return True if successfully deleted, false if not found
     */
    boolean deleteGeofenceDeployment(String geofenceId);

    /**
     * Get all active Geofence deployments for a user
     * 
     * @param deployedBy The username
     * @return List of active Geofence deployment responses
     */
    List<GeofenceDeploymentResponse> getActiveGeofenceDeploymentsByUser(String deployedBy);

    /**
     * Deactivate all expired Geofence deployments
     * 
     * @return Number of Geofence deployments deactivated
     */
    int deactivateExpiredGeofenceDeployments();

    /**
     * Get all expired active Geofence deployments
     * 
     * @return List of expired active Geofence deployment responses
     */
    List<GeofenceDeploymentResponse> getExpiredGeofenceDeployments();

    /**
     * Get all active Geofence deployments
     * 
     * @return List of all active Geofence deployment responses
     */
    List<GeofenceDeploymentResponse> getActiveGeofenceDeployments();
}
