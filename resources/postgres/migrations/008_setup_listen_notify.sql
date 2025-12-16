-- PostgreSQL LISTEN/NOTIFY setup for CV-MEC geohash cache invalidation
-- This script creates the necessary triggers and functions to send notifications
-- when the relevant tables are updated

-- Function to send notifications on table updates
CREATE OR REPLACE FUNCTION notify_table_update()
RETURNS TRIGGER AS $$
BEGIN
    -- Send notification with table name and operation type
    PERFORM pg_notify('table_updates', TG_TABLE_NAME || ':' || TG_OP);
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- Drop existing triggers if they exist
DROP TRIGGER IF EXISTS geofence_deployments_notify ON geofence_deployments;
DROP TRIGGER IF EXISTS geofence_geohashes_notify ON geofence_geohashes;

-- Create triggers for geofence_deployments table
-- This will notify when deployments are inserted, updated, or deleted
-- Especially important for is_active status changes
CREATE TRIGGER geofence_deployments_notify
    AFTER INSERT OR UPDATE OR DELETE ON geofence_deployments
    FOR EACH ROW
    EXECUTE FUNCTION notify_table_update();

-- Create triggers for geofence_geohashes table  
-- This will notify when geohash associations change
CREATE TRIGGER geofence_geohashes_notify
    AFTER INSERT OR UPDATE OR DELETE ON geofence_geohashes
    FOR EACH ROW
    EXECUTE FUNCTION notify_table_update();
