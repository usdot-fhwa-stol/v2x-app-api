-- Migration 007: Add message_types lookup and msg_type to deployments, backfill TIM

-- 1) Lookup table for allowed V2X message types
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

-- 2) Add msg_type column (nullable initially for backfill)
ALTER TABLE geofence_deployments
ADD COLUMN IF NOT EXISTS msg_type TEXT;

-- 3) Backfill existing rows to TIM when msg_type is NULL or empty
UPDATE geofence_deployments
SET msg_type = 'TIM'
WHERE msg_type IS NULL OR LENGTH(TRIM(msg_type)) = 0;

-- 4) Enforce NOT NULL after backfill
ALTER TABLE geofence_deployments
ALTER COLUMN msg_type SET NOT NULL;

-- 5) Add FK constraint to lookup table
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_geofence_deployments_msg_type'
          AND table_name = 'geofence_deployments'
    ) THEN
        ALTER TABLE geofence_deployments
        ADD CONSTRAINT fk_geofence_deployments_msg_type FOREIGN KEY (msg_type)
        REFERENCES message_types(code);
    END IF;
END $$;

-- 6) Helpful index for msg_type filtering
CREATE INDEX IF NOT EXISTS idx_geofence_deployments_msg_type ON geofence_deployments(msg_type);


