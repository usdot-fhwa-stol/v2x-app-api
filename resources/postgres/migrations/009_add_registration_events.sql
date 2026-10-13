-- Migration 009: Add registration_events table and enforce one active row per device
-- registration_events is an append-only log of registration lifecycle actions
-- (register, connect, evict, expire, reconcile, failures) used to correlate
-- registration activity with message loss.

CREATE TABLE IF NOT EXISTS registration_events (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(255),
    vendor_id VARCHAR(255),
    requested_by VARCHAR(255),
    event_type VARCHAR(50) NOT NULL,
    reason TEXT,
    etx_status INTEGER,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_registration_events_device_created ON registration_events(device_id, created_at);
CREATE INDEX IF NOT EXISTS idx_registration_events_vendor_created ON registration_events(vendor_id, created_at);
CREATE INDEX IF NOT EXISTS idx_registration_events_type ON registration_events(event_type);

-- Deactivate duplicate active rows for the same device, keeping the newest
UPDATE registration_logs r
SET is_active = FALSE,
    unregister_at = CURRENT_TIMESTAMP
WHERE r.is_active = TRUE
  AND EXISTS (
      SELECT 1 FROM registration_logs n
      WHERE n.device_id = r.device_id
        AND n.is_active = TRUE
        AND n.id > r.id
  );

-- At most one active registration log per device
CREATE UNIQUE INDEX IF NOT EXISTS uq_registration_logs_active_device
    ON registration_logs(device_id) WHERE is_active = TRUE;
