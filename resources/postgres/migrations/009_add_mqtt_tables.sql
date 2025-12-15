-- Migration: Add MQTT client and ACL tables
-- Description: Creates tables for managing MQTT client registrations and ACL entries

-- Create mqtt_clients table for storing MQTT client registrations
CREATE TABLE IF NOT EXISTS mqtt_clients (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    certificate_cn VARCHAR(255),
    certificate_serial VARCHAR(255),
    certificate_expires_at TIMESTAMP,
    last_connected_at TIMESTAMP,
    -- Constraints
    CONSTRAINT chk_mqtt_clients_client_id CHECK (client_id IS NOT NULL AND LENGTH(TRIM(client_id)) > 0),
    CONSTRAINT chk_mqtt_clients_username CHECK (username IS NOT NULL AND LENGTH(TRIM(username)) > 0)
);

-- Create mqtt_acl_entries table for storing ACL rules
CREATE TABLE IF NOT EXISTS mqtt_acl_entries (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    topic_pattern VARCHAR(512) NOT NULL,
    access_type VARCHAR(10) NOT NULL, -- 'read', 'write', 'readwrite'
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    -- Foreign key constraint
    CONSTRAINT fk_mqtt_acl_entries_client_id FOREIGN KEY (client_id) 
        REFERENCES mqtt_clients(client_id) ON DELETE CASCADE,
    -- Constraints
    CONSTRAINT chk_mqtt_acl_entries_topic_pattern CHECK (topic_pattern IS NOT NULL AND LENGTH(TRIM(topic_pattern)) > 0),
    CONSTRAINT chk_mqtt_acl_entries_access_type CHECK (access_type IN ('read', 'write', 'readwrite')),
    -- Ensure unique combination of client_id, topic_pattern, and access_type
    CONSTRAINT uk_mqtt_acl_entries_client_topic_access UNIQUE (client_id, topic_pattern, access_type)
);

-- Create indexes for mqtt_clients table
CREATE INDEX IF NOT EXISTS idx_mqtt_clients_client_id ON mqtt_clients(client_id);
CREATE INDEX IF NOT EXISTS idx_mqtt_clients_username ON mqtt_clients(username);
CREATE INDEX IF NOT EXISTS idx_mqtt_clients_created_by ON mqtt_clients(created_by);
CREATE INDEX IF NOT EXISTS idx_mqtt_clients_is_active ON mqtt_clients(is_active);
CREATE INDEX IF NOT EXISTS idx_mqtt_clients_certificate_cn ON mqtt_clients(certificate_cn);

-- Create indexes for mqtt_acl_entries table
CREATE INDEX IF NOT EXISTS idx_mqtt_acl_entries_client_id ON mqtt_acl_entries(client_id);
CREATE INDEX IF NOT EXISTS idx_mqtt_acl_entries_topic_pattern ON mqtt_acl_entries(topic_pattern);
CREATE INDEX IF NOT EXISTS idx_mqtt_acl_entries_access_type ON mqtt_acl_entries(access_type);
CREATE INDEX IF NOT EXISTS idx_mqtt_acl_entries_is_active ON mqtt_acl_entries(is_active);
CREATE INDEX IF NOT EXISTS idx_mqtt_acl_entries_created_by ON mqtt_acl_entries(created_by);

-- Create composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_mqtt_acl_entries_client_active ON mqtt_acl_entries(client_id, is_active);
CREATE INDEX IF NOT EXISTS idx_mqtt_clients_created_by_active ON mqtt_clients(created_by, is_active);

-- Add trigger to automatically update updated_at timestamp for mqtt_clients
CREATE OR REPLACE FUNCTION update_mqtt_clients_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_mqtt_clients_updated_at 
    BEFORE UPDATE ON mqtt_clients 
    FOR EACH ROW 
    EXECUTE FUNCTION update_mqtt_clients_updated_at();

-- Add trigger to automatically update updated_at timestamp for mqtt_acl_entries
CREATE OR REPLACE FUNCTION update_mqtt_acl_entries_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_mqtt_acl_entries_updated_at 
    BEFORE UPDATE ON mqtt_acl_entries 
    FOR EACH ROW 
    EXECUTE FUNCTION update_mqtt_acl_entries_updated_at();

-- Add function to get ACL entries for a client
CREATE OR REPLACE FUNCTION get_mqtt_acl_entries(p_client_id VARCHAR(255))
RETURNS TABLE (
    topic_pattern VARCHAR(512),
    access_type VARCHAR(10)
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        mae.topic_pattern, mae.access_type
    FROM mqtt_acl_entries mae
    WHERE mae.client_id = p_client_id AND mae.is_active = TRUE
    ORDER BY mae.topic_pattern;
END;
$$ LANGUAGE plpgsql;

