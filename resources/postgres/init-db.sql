-- Database initialization script for the v2x-app API
-- This script sets up the PostgreSQL database for both Keycloak and the v2x-app API

-- Create the keycloak schema for Keycloak tables
CREATE SCHEMA IF NOT EXISTS keycloak;

-- Grant permissions to the application user
GRANT ALL PRIVILEGES ON SCHEMA keycloak TO admin_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA keycloak TO admin_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA keycloak TO admin_user;

-- Grant permissions for future tables in the keycloak schema
ALTER DEFAULT PRIVILEGES IN SCHEMA keycloak GRANT ALL ON TABLES TO admin_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA keycloak GRANT ALL ON SEQUENCES TO admin_user;

-- Grant permissions for the public schema (for error_logs table)
GRANT ALL PRIVILEGES ON SCHEMA public TO admin_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO admin_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO admin_user;

-- Grant permissions for future tables in the public schema
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO admin_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO admin_user;

-- Create the error_logs table with simplified schema (no user_id or session_id)
CREATE TABLE IF NOT EXISTS error_logs (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL,
    error_type VARCHAR NOT NULL,
    error_message VARCHAR(1000) NOT NULL,
    stack_trace TEXT,
    request_path VARCHAR,
    request_method VARCHAR,
    request_body TEXT,
    user_agent VARCHAR,
    client_ip VARCHAR,
    severity VARCHAR NOT NULL
);

-- Create indexes for better performance on error_logs table
CREATE INDEX IF NOT EXISTS idx_error_logs_timestamp ON error_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_error_logs_severity ON error_logs(severity);
CREATE INDEX IF NOT EXISTS idx_error_logs_error_type ON error_logs(error_type);
CREATE INDEX IF NOT EXISTS idx_error_logs_request_path ON error_logs(request_path);

-- Create the registration_logs table for tracking ETX registrations
CREATE TABLE IF NOT EXISTS registration_logs (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    client_type VARCHAR(50) NOT NULL,
    client_subtype VARCHAR(50) NOT NULL,
    vendor_id VARCHAR(255) NOT NULL,
    requested_by VARCHAR(255) NOT NULL,
    registration_count INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    unregister_at TIMESTAMP,
    last_connected TIMESTAMP
);

-- Create indexes for better performance on registration_logs table
CREATE INDEX IF NOT EXISTS idx_registration_logs_device_id ON registration_logs(device_id);
CREATE INDEX IF NOT EXISTS idx_registration_logs_vendor_id ON registration_logs(vendor_id);
CREATE INDEX IF NOT EXISTS idx_registration_logs_requested_by ON registration_logs(requested_by);
CREATE INDEX IF NOT EXISTS idx_registration_logs_client_type ON registration_logs(client_type);
CREATE INDEX IF NOT EXISTS idx_registration_logs_is_active ON registration_logs(is_active);
CREATE INDEX IF NOT EXISTS idx_registration_logs_created_at ON registration_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_registration_logs_expires_at ON registration_logs(expires_at);
CREATE INDEX IF NOT EXISTS idx_registration_logs_last_connected ON registration_logs(last_connected);

-- Create composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_registration_logs_user_active ON registration_logs(requested_by, is_active);
CREATE INDEX IF NOT EXISTS idx_registration_logs_vendor_active ON registration_logs(vendor_id, is_active);
CREATE INDEX IF NOT EXISTS idx_registration_logs_vendor_type_active ON registration_logs(vendor_id, client_type, is_active);
CREATE INDEX IF NOT EXISTS idx_registration_logs_user_last_connected ON registration_logs(requested_by, last_connected, is_active);
CREATE INDEX IF NOT EXISTS idx_registration_logs_vendor_last_connected ON registration_logs(vendor_id, last_connected, is_active);

-- Create the vendor_limits table for vendor-wide registration limits
CREATE TABLE IF NOT EXISTS vendor_limits (
    id BIGSERIAL PRIMARY KEY,
    vendor_id VARCHAR(255) NOT NULL UNIQUE,
    max_registrations INTEGER NOT NULL DEFAULT 50,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    -- Ensure vendor_id is not null and has reasonable constraints
    CONSTRAINT chk_vendor_limits_vendor_id CHECK (vendor_id IS NOT NULL AND LENGTH(TRIM(vendor_id)) > 0),
    CONSTRAINT chk_vendor_limits_max_registrations CHECK (max_registrations > 0)
);

-- Create indexes for vendor_limits table
CREATE INDEX IF NOT EXISTS idx_vendor_limits_vendor_id ON vendor_limits(vendor_id);
CREATE INDEX IF NOT EXISTS idx_vendor_limits_is_active ON vendor_limits(is_active);

-- Create the user_limits table for configurable user restrictions
CREATE TABLE IF NOT EXISTS user_limits (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    vendor_id VARCHAR(255) NOT NULL,
    max_registrations INTEGER NOT NULL DEFAULT 5,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    -- Ensure unique combination of username and vendor_id
    CONSTRAINT uk_user_limits_username_vendor UNIQUE (username, vendor_id),
    -- Foreign key constraint to ensure vendor exists in vendor_limits
    CONSTRAINT fk_user_limits_vendor_id FOREIGN KEY (vendor_id) 
        REFERENCES vendor_limits(vendor_id) ON DELETE CASCADE
);

-- Create indexes for user_limits table
CREATE INDEX IF NOT EXISTS idx_user_limits_username ON user_limits(username);
CREATE INDEX IF NOT EXISTS idx_user_limits_vendor_id ON user_limits(vendor_id);
CREATE INDEX IF NOT EXISTS idx_user_limits_is_active ON user_limits(is_active);
CREATE INDEX IF NOT EXISTS idx_user_limits_username_vendor ON user_limits(username, vendor_id);
CREATE INDEX IF NOT EXISTS idx_user_limits_user_vendor_active ON user_limits(username, vendor_id, is_active);

-- Add trigger function for updating timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Add triggers for automatic timestamp updates
CREATE TRIGGER update_vendor_limits_updated_at 
    BEFORE UPDATE ON vendor_limits 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_limits_updated_at 
    BEFORE UPDATE ON user_limits 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Create the geofence_deployments table for V2X message deployments
CREATE TABLE IF NOT EXISTS geofence_deployments (
    id BIGSERIAL PRIMARY KEY,
    geofence_id VARCHAR(255) NOT NULL UNIQUE,
    geojson JSONB NOT NULL,
    msg_type TEXT NOT NULL,
    hex_payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deployed_by VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMP,
    -- Constraints
    CONSTRAINT chk_geofence_deployments_geofence_id CHECK (geofence_id IS NOT NULL AND LENGTH(TRIM(geofence_id)) > 0),
    CONSTRAINT chk_geofence_deployments_geojson CHECK (geojson IS NOT NULL),
    CONSTRAINT chk_geofence_deployments_hex_payload CHECK (hex_payload IS NOT NULL AND LENGTH(TRIM(hex_payload)) > 0),
    CONSTRAINT chk_geofence_deployments_deployed_by CHECK (deployed_by IS NOT NULL AND LENGTH(TRIM(deployed_by)) > 0)
);

-- Lookup table for allowed V2X message types
CREATE TABLE IF NOT EXISTS message_types (
    code TEXT PRIMARY KEY,
    description TEXT,
    asn_class TEXT UNIQUE
);

-- Seed allowed message types
INSERT INTO message_types (code, description, asn_class) VALUES
    ('TIM', 'Traveler Information Message', 'us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame'),
    ('MAP', 'MAP Message', 'us.dot.its.jpo.asn.j2735.r2024.MapData.MapDataMessageFrame')
ON CONFLICT (code) DO NOTHING;

-- Create the geofence_geohashes table for storing individual geohashes with references to geofence deployments
CREATE TABLE IF NOT EXISTS geofence_geohashes (
    id BIGSERIAL PRIMARY KEY,
    geofence_deployment_id BIGINT NOT NULL,
    geohash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Constraints
    CONSTRAINT chk_geofence_geohashes_geohash CHECK (geohash IS NOT NULL AND LENGTH(TRIM(geohash)) > 0),
    -- Foreign key constraint to ensure deployment exists
    CONSTRAINT fk_geofence_geohashes_deployment_id FOREIGN KEY (geofence_deployment_id) 
        REFERENCES geofence_deployments(id) ON DELETE CASCADE,
    -- Ensure unique combination of deployment and geohash
    CONSTRAINT uk_geofence_geohashes_deployment_geohash UNIQUE (geofence_deployment_id, geohash)
);

-- Create indexes for geofence_geohashes table
CREATE INDEX IF NOT EXISTS idx_geofence_geohashes_geofence_deployment_id ON geofence_geohashes(geofence_deployment_id);
CREATE INDEX IF NOT EXISTS idx_geofence_geohashes_geohash ON geofence_geohashes(geohash);

-- Create indexes for geofence_deployments table
CREATE INDEX IF NOT EXISTS idx_geofence_deployments_geofence_id ON geofence_deployments(geofence_id);
CREATE INDEX IF NOT EXISTS idx_geofence_deployments_deployed_by ON geofence_deployments(deployed_by);
CREATE INDEX IF NOT EXISTS idx_geofence_deployments_is_active ON geofence_deployments(is_active);
CREATE INDEX IF NOT EXISTS idx_geofence_deployments_created_at ON geofence_deployments(created_at);
CREATE INDEX IF NOT EXISTS idx_geofence_deployments_expires_at ON geofence_deployments(expires_at);
CREATE INDEX IF NOT EXISTS idx_geofence_deployments_msg_type ON geofence_deployments(msg_type);

-- Create GIN index for JSONB column for efficient geospatial queries
CREATE INDEX IF NOT EXISTS idx_geofence_deployments_geojson ON geofence_deployments USING GIN (geojson);

-- Create composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_geofence_deployments_user_active ON geofence_deployments(deployed_by, is_active);
CREATE INDEX IF NOT EXISTS idx_geofence_deployments_expires_active ON geofence_deployments(expires_at, is_active);

-- Add trigger to automatically update updated_at timestamp for geofence_deployments
CREATE TRIGGER update_geofence_deployments_updated_at 
    BEFORE UPDATE ON geofence_deployments 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Add function to deactivate a geofence deployment
CREATE OR REPLACE FUNCTION deactivate_geofence_deployment(
    p_geofence_id VARCHAR(255)
) RETURNS BOOLEAN AS $$
DECLARE
    v_updated_rows INTEGER;
BEGIN
    -- Update the geofence deployment to set is_active = false
    UPDATE geofence_deployments 
    SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP
    WHERE geofence_id = p_geofence_id AND is_active = TRUE;
    
    -- Get the number of updated rows
    GET DIAGNOSTICS v_updated_rows = ROW_COUNT;
    
    -- Return true if a row was updated, false otherwise
    RETURN v_updated_rows > 0;
END;
$$ LANGUAGE plpgsql;

-- Add function to get geofence deployments by geohash
CREATE OR REPLACE FUNCTION get_geofence_deployments_by_geohash(p_geohash VARCHAR(255))
RETURNS TABLE (
    geofence_id VARCHAR(255),
    geojson JSONB,
    hex_payload TEXT,
    created_at TIMESTAMP,
    deployed_by VARCHAR(255),
    is_active BOOLEAN
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        gd.geofence_id, gd.geojson, gd.hex_payload, 
        gd.created_at, gd.deployed_by, gd.is_active
    FROM geofence_deployments gd
    INNER JOIN geofence_geohashes gg ON gd.id = gg.geofence_deployment_id
    WHERE gg.geohash = p_geohash;
END;
$$ LANGUAGE plpgsql;

-- Add function to get all geohashes for a geofence deployment
CREATE OR REPLACE FUNCTION get_geofence_geohashes(
    p_geofence_id VARCHAR(255)
) RETURNS TABLE (
    geohash VARCHAR(255)
) AS $$
BEGIN
    RETURN QUERY
    SELECT gg.geohash
    FROM geofence_geohashes gg
    INNER JOIN geofence_deployments gd ON gg.geofence_deployment_id = gd.id
    WHERE gd.geofence_id = p_geofence_id;
END;
$$ LANGUAGE plpgsql;

-- Create the paths table for storing GeoJSON-like path data
CREATE TABLE IF NOT EXISTS paths (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'Feature',
    geometry_type VARCHAR(50) NOT NULL DEFAULT 'LineString',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    -- Constraints
    CONSTRAINT chk_paths_name CHECK (name IS NOT NULL AND LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_paths_type CHECK (type IS NOT NULL AND LENGTH(TRIM(type)) > 0),
    CONSTRAINT chk_paths_geometry_type CHECK (geometry_type IS NOT NULL AND LENGTH(TRIM(geometry_type)) > 0)
);

-- Create the path_coordinates table for storing coordinate data
CREATE TABLE IF NOT EXISTS path_coordinates (
    id BIGSERIAL PRIMARY KEY,
    path_id BIGINT NOT NULL,
    coordinate VARCHAR(255) NOT NULL,
    -- Foreign key constraint to ensure path exists
    CONSTRAINT fk_path_coordinates_path_id FOREIGN KEY (path_id) 
        REFERENCES paths(id) ON DELETE CASCADE,
    -- Ensure coordinate is not null and has reasonable format
    CONSTRAINT chk_path_coordinates_coordinate CHECK (coordinate IS NOT NULL AND LENGTH(TRIM(coordinate)) > 0)
);

-- Create the path_timestamps table for storing timestamp data
CREATE TABLE IF NOT EXISTS path_timestamps (
    id BIGSERIAL PRIMARY KEY,
    path_id BIGINT NOT NULL,
    timestamp BIGINT NOT NULL,
    -- Foreign key constraint to ensure path exists
    CONSTRAINT fk_path_timestamps_path_id FOREIGN KEY (path_id) 
        REFERENCES paths(id) ON DELETE CASCADE,
    -- Ensure timestamp is not null and non-negative
    CONSTRAINT chk_path_timestamps_timestamp CHECK (timestamp IS NOT NULL AND timestamp >= 0)
);

-- Create indexes for paths table
CREATE INDEX IF NOT EXISTS idx_paths_name ON paths(name);
CREATE INDEX IF NOT EXISTS idx_paths_is_active ON paths(is_active);
CREATE INDEX IF NOT EXISTS idx_paths_created_at ON paths(created_at);
CREATE INDEX IF NOT EXISTS idx_paths_created_by ON paths(created_by);
CREATE INDEX IF NOT EXISTS idx_paths_updated_by ON paths(updated_by);

-- Create indexes for path_coordinates table
CREATE INDEX IF NOT EXISTS idx_path_coordinates_path_id ON path_coordinates(path_id);

-- Create indexes for path_timestamps table
CREATE INDEX IF NOT EXISTS idx_path_timestamps_path_id ON path_timestamps(path_id);

-- Create composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_paths_name_active ON paths(name, is_active);
CREATE INDEX IF NOT EXISTS idx_paths_created_by_active ON paths(created_by, is_active);

-- Add trigger to automatically update updated_at timestamp for paths
CREATE TRIGGER update_paths_updated_at 
    BEFORE UPDATE ON paths 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Verify the setup
SELECT current_database(), current_user;
SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('public', 'keycloak');

-- -----------------------------------------------------------------------------
-- Active geohash lookup helpers for collision avoidance during deployment
-- -----------------------------------------------------------------------------

-- View of currently active geohashes (joins deployments and filters active/valid window)
CREATE OR REPLACE VIEW active_geofence_geohashes AS
SELECT DISTINCT gg.geohash
FROM geofence_geohashes gg
JOIN geofence_deployments gd ON gg.geofence_deployment_id = gd.id
WHERE gd.is_active = TRUE
  AND (gd.expires_at IS NULL OR gd.expires_at > CURRENT_TIMESTAMP);

-- Function: return all active geohashes
CREATE OR REPLACE FUNCTION get_active_geohashes()
RETURNS TABLE (geohash VARCHAR)
LANGUAGE sql
AS $$
    SELECT geohash FROM active_geofence_geohashes;
$$;

-- Function: check if a specific geohash is active
CREATE OR REPLACE FUNCTION is_geohash_active(p_geohash VARCHAR)
RETURNS BOOLEAN
LANGUAGE sql
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM geofence_geohashes gg
        JOIN geofence_deployments gd ON gg.geofence_deployment_id = gd.id
        WHERE gg.geohash = p_geohash
          AND gd.is_active = TRUE
          AND (gd.expires_at IS NULL OR gd.expires_at > CURRENT_TIMESTAMP)
    );
$$;

-- Function: return active geohashes matching a given prefix (e.g., level prefix)
CREATE OR REPLACE FUNCTION get_active_geohashes_by_prefix(p_prefix VARCHAR)
RETURNS TABLE (geohash VARCHAR)
LANGUAGE sql
AS $$
    SELECT gg.geohash
    FROM geofence_geohashes gg
    JOIN geofence_deployments gd ON gg.geofence_deployment_id = gd.id
    WHERE gg.geohash LIKE (p_prefix || '%')
      AND gd.is_active = TRUE
      AND (gd.expires_at IS NULL OR gd.expires_at > CURRENT_TIMESTAMP)
    GROUP BY gg.geohash;
$$;

-- Function: return active geohashes intersecting a provided list
CREATE OR REPLACE FUNCTION get_active_geohashes_for_list(p_geohashes VARCHAR[])
RETURNS TABLE (geohash VARCHAR)
LANGUAGE sql
AS $$
    SELECT gg.geohash
    FROM geofence_geohashes gg
    JOIN geofence_deployments gd ON gg.geofence_deployment_id = gd.id
    WHERE gg.geohash = ANY(p_geohashes)
      AND gd.is_active = TRUE
      AND (gd.expires_at IS NULL OR gd.expires_at > CURRENT_TIMESTAMP)
    GROUP BY gg.geohash;
$$;

-- Helpful indexes (some already exist); ensure optimal filtering
-- geofence_geohashes(geohash) exists (idx_geofence_geohashes_geohash)
-- geofence_deployments(is_active) exists (idx_geofence_deployments_is_active)
-- geofence_deployments(expires_at) exists (idx_geofence_deployments_expires_at)
-- Optional: composite to speed active filtering with join
CREATE INDEX IF NOT EXISTS idx_geofence_deployments_active_expires
    ON geofence_deployments(is_active, expires_at);

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
