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