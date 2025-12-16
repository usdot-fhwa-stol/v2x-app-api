-- Migration 004: Add vendor_limits table for vendor-wide registration limits
-- This table manages vendor-wide limits separately from user limits
-- This table must be created before user_limits due to foreign key dependency

-- Create the vendor_limits table
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

-- Create indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_vendor_limits_vendor_id ON vendor_limits(vendor_id);
CREATE INDEX IF NOT EXISTS idx_vendor_limits_is_active ON vendor_limits(is_active);

-- Add trigger to automatically update updated_at timestamp
CREATE TRIGGER update_vendor_limits_updated_at 
    BEFORE UPDATE ON vendor_limits 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();
