-- Migration 001: Add registration_logs table
-- Simple migration script for adding registration tracking

-- Create the registration_logs table
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

-- Create essential indexes
CREATE INDEX IF NOT EXISTS idx_registration_logs_device_id ON registration_logs(device_id);
CREATE INDEX IF NOT EXISTS idx_registration_logs_vendor_id ON registration_logs(vendor_id);
CREATE INDEX IF NOT EXISTS idx_registration_logs_requested_by ON registration_logs(requested_by);
CREATE INDEX IF NOT EXISTS idx_registration_logs_is_active ON registration_logs(is_active);
CREATE INDEX IF NOT EXISTS idx_registration_logs_created_at ON registration_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_registration_logs_last_connected ON registration_logs(last_connected);

-- Composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_registration_logs_user_active ON registration_logs(requested_by, is_active);
CREATE INDEX IF NOT EXISTS idx_registration_logs_vendor_active ON registration_logs(vendor_id, is_active);
CREATE INDEX IF NOT EXISTS idx_registration_logs_user_last_connected ON registration_logs(requested_by, last_connected, is_active);
CREATE INDEX IF NOT EXISTS idx_registration_logs_vendor_last_connected ON registration_logs(vendor_id, last_connected, is_active);
