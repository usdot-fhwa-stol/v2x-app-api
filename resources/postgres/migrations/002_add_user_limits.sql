-- Migration 003: Add user_limits table for configurable user restrictions
-- This table allows setting custom registration limits per user
-- Users can only be assigned to vendors that have vendor_limits entries

-- Create the user_limits table
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

-- Create indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_user_limits_username ON user_limits(username);
CREATE INDEX IF NOT EXISTS idx_user_limits_vendor_id ON user_limits(vendor_id);
CREATE INDEX IF NOT EXISTS idx_user_limits_is_active ON user_limits(is_active);
CREATE INDEX IF NOT EXISTS idx_user_limits_username_vendor ON user_limits(username, vendor_id);

-- Create composite index for user lookup
CREATE INDEX IF NOT EXISTS idx_user_limits_user_vendor_active ON user_limits(username, vendor_id, is_active);

-- Add trigger to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_user_limits_updated_at 
    BEFORE UPDATE ON user_limits 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();
