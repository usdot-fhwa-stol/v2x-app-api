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
