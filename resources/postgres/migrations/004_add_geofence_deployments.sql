-- Migration 004: Add Geofence deployment table for V2X message deployments
-- This migration creates a table to handle V2X message deployment configuration
-- Supports TIM, MAP, and other V2X message types
-- Uses a single table with is_active flag for simplicity

-- Create the geofence_deployments table for V2X message deployments
CREATE TABLE IF NOT EXISTS geofence_deployments (
    id BIGSERIAL PRIMARY KEY,
    geofence_id VARCHAR(255) NOT NULL UNIQUE,
    geojson JSONB NOT NULL,
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
