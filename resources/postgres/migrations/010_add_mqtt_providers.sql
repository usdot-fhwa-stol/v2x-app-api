-- Migration 010: MQTT provider configuration (ETX, NMI, AV)
--
-- Stores the MQTT/REST providers the mobile app pulls at runtime from the
-- /prd/v3/providers endpoints, their ACL topic templates and their encrypted
-- credentials. Also adds a J2735 messageId reference table.
--
-- Idempotent: safe to re-run. Seed rows never overwrite admin edits.
-- Secrets are never seeded; they are entered through the admin API and stored
-- AES-256-GCM encrypted by the application.
BEGIN;

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 1) Providers --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mqtt_providers (
    id BIGSERIAL PRIMARY KEY,
    provider_key VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    provider_type VARCHAR(32) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    -- STATIC: host/port below; ETX_CONNECTION: app uses POST /prd/v2/connection
    connection_source VARCHAR(32) NOT NULL DEFAULT 'STATIC',
    mqtt_host VARCHAR(255),
    mqtt_protocol VARCHAR(8),
    mqtt_port INTEGER,
    tls_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    -- NULL means anonymous
    mqtt_auth_type VARCHAR(32),
    payload_format VARCHAR(64) NOT NULL,
    rest_base_url VARCHAR(1024),
    rest_auth_type VARCHAR(32),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT chk_mqtt_providers_provider_key CHECK (provider_key ~ '^[a-z0-9-]+$'),
    CONSTRAINT chk_mqtt_providers_display_name CHECK (LENGTH(TRIM(display_name)) > 0),
    CONSTRAINT chk_mqtt_providers_mqtt_protocol CHECK (mqtt_protocol IS NULL OR mqtt_protocol IN ('tcp', 'ssl', 'ws', 'wss')),
    CONSTRAINT chk_mqtt_providers_mqtt_port CHECK (mqtt_port IS NULL OR mqtt_port BETWEEN 1 AND 65535),
    -- An enabled STATIC provider must say where to connect.
    CONSTRAINT chk_mqtt_providers_static_connection CHECK (
        connection_source <> 'STATIC' OR enabled = FALSE
        OR (mqtt_host IS NOT NULL AND mqtt_protocol IS NOT NULL AND mqtt_port IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_mqtt_providers_active_enabled ON mqtt_providers(is_active, enabled, priority);

CREATE OR REPLACE TRIGGER update_mqtt_providers_updated_at
    BEFORE UPDATE ON mqtt_providers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Named REST endpoints relative to rest_base_url (e.g. tim -> /tims)
CREATE TABLE IF NOT EXISTS mqtt_provider_rest_endpoints (
    provider_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    path VARCHAR(1024) NOT NULL,
    PRIMARY KEY (provider_id, name),
    CONSTRAINT fk_mqtt_provider_rest_endpoints_provider_id FOREIGN KEY (provider_id)
        REFERENCES mqtt_providers(id) ON DELETE CASCADE
);

-- 2) ACL rules ----------------------------------------------------------------
-- Topic templates use ETX syntax plus '@', a whole level that the app expands
-- to the '/'-delimited geohash path (see TopicTemplateValidator).
CREATE TABLE IF NOT EXISTS mqtt_provider_acl_rules (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    subscribe_limit INTEGER,
    publish_rate_limit INTEGER,
    geohash_symbol VARCHAR(8) NOT NULL DEFAULT '@',
    geohash_levels INTEGER NOT NULL DEFAULT 7,
    publish_precision INTEGER NOT NULL DEFAULT 7,
    subscribe_precision INTEGER NOT NULL DEFAULT 7,
    -- rings of neighbour cells around the current cell; 1 = 3x3 grid
    subscribe_grid_radius INTEGER NOT NULL DEFAULT 1,
    -- comma-separated USER/DEPOSITOR/ADMIN; NULL applies to every role
    roles VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT uk_mqtt_provider_acl_rules_provider_name UNIQUE (provider_id, name),
    CONSTRAINT fk_mqtt_provider_acl_rules_provider_id FOREIGN KEY (provider_id)
        REFERENCES mqtt_providers(id) ON DELETE CASCADE,
    CONSTRAINT chk_mqtt_provider_acl_rules_name CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_mqtt_provider_acl_rules_limits CHECK (
        (subscribe_limit IS NULL OR subscribe_limit > 0)
        AND (publish_rate_limit IS NULL OR publish_rate_limit > 0)
    ),
    CONSTRAINT chk_mqtt_provider_acl_rules_geohash CHECK (
        geohash_levels BETWEEN 1 AND 12
        AND publish_precision BETWEEN 1 AND geohash_levels
        AND subscribe_precision BETWEEN 1 AND geohash_levels
        AND subscribe_grid_radius BETWEEN 0 AND 2
    )
);

CREATE INDEX IF NOT EXISTS idx_mqtt_provider_acl_rules_provider_id ON mqtt_provider_acl_rules(provider_id);

CREATE OR REPLACE TRIGGER update_mqtt_provider_acl_rules_updated_at
    BEFORE UPDATE ON mqtt_provider_acl_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE IF NOT EXISTS mqtt_provider_acl_publish_topics (
    rule_id BIGINT NOT NULL,
    topic_order INTEGER NOT NULL,
    topic VARCHAR(1024) NOT NULL,
    PRIMARY KEY (rule_id, topic_order),
    CONSTRAINT fk_mqtt_provider_acl_publish_topics_rule_id FOREIGN KEY (rule_id)
        REFERENCES mqtt_provider_acl_rules(id) ON DELETE CASCADE,
    CONSTRAINT chk_mqtt_provider_acl_publish_topics_topic CHECK (LENGTH(TRIM(topic)) > 0)
);

CREATE TABLE IF NOT EXISTS mqtt_provider_acl_subscribe_topics (
    rule_id BIGINT NOT NULL,
    topic_order INTEGER NOT NULL,
    topic VARCHAR(1024) NOT NULL,
    PRIMARY KEY (rule_id, topic_order),
    CONSTRAINT fk_mqtt_provider_acl_subscribe_topics_rule_id FOREIGN KEY (rule_id)
        REFERENCES mqtt_provider_acl_rules(id) ON DELETE CASCADE,
    CONSTRAINT chk_mqtt_provider_acl_subscribe_topics_topic CHECK (LENGTH(TRIM(topic)) > 0)
);

-- 3) Credentials (one shared set per provider) --------------------------------
-- *_enc columns hold v{keyVersion}:base64(iv || ciphertext || tag)
CREATE TABLE IF NOT EXISTS mqtt_provider_credentials (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL UNIQUE,
    mqtt_username VARCHAR(255),
    mqtt_password_enc TEXT,
    rest_username VARCHAR(255),
    rest_client_id VARCHAR(255),
    rest_secret_enc TEXT,
    rest_api_key_header VARCHAR(255),
    rest_token_url VARCHAR(1024),
    rest_scope VARCHAR(1024),
    key_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_mqtt_provider_credentials_provider_id FOREIGN KEY (provider_id)
        REFERENCES mqtt_providers(id) ON DELETE CASCADE
);

CREATE OR REPLACE TRIGGER update_mqtt_provider_credentials_updated_at
    BEFORE UPDATE ON mqtt_provider_credentials
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 4) J2735 messageId reference --------------------------------------------------
-- Kept separate from message_types, which is the FK domain for deposit msg_type.
CREATE TABLE IF NOT EXISTS j2735_message_ids (
    code VARCHAR(16) PRIMARY KEY,
    message_id INTEGER NOT NULL UNIQUE,
    description TEXT
);

INSERT INTO j2735_message_ids (code, message_id, description) VALUES
    ('MAP', 18, 'MAP Data'),
    ('SPAT', 19, 'Signal Phase and Timing'),
    ('BSM', 20, 'Basic Safety Message'),
    ('TIM', 31, 'Traveler Information Message'),
    ('PSM', 32, 'Personal Safety Message'),
    ('SDSM', 41, 'Sensor Data Sharing Message')
ON CONFLICT (code) DO NOTHING;

-- 5) Seed providers and their default ACL rules ---------------------------------
-- Child rows are inserted only when the provider row is created by this run, so
-- re-running never resurrects endpoints, rules or topics an admin has changed.
DO $$
DECLARE
    v_provider_id BIGINT;
    v_rule_id BIGINT;
BEGIN
    INSERT INTO mqtt_providers (provider_key, display_name, provider_type, priority, enabled,
            connection_source, mqtt_auth_type, payload_format, created_by, updated_by)
    VALUES ('etx', 'ETX', 'ETX', 0, TRUE,
            'ETX_CONNECTION', 'ETX_CERT', 'GEO_ROUTED_PROTO_UNSIGNED_UPER', 'migration-010', 'migration-010')
    ON CONFLICT (provider_key) DO NOTHING;
    -- ETX ACL rules come from ETX itself (GET /prd/v2/acl-rules); nothing to seed.

    -- NMI: v1/g32/{geohash}/{messageId}. Subscribe TIM|SPAT|MAP|SDSM over a
    -- 3x3 grid = 4 x 9 = 36 subscriptions.
    INSERT INTO mqtt_providers (provider_key, display_name, provider_type, priority, enabled,
            connection_source, mqtt_host, mqtt_protocol, mqtt_port, tls_enabled,
            payload_format, created_by, updated_by)
    VALUES ('nmi', 'OmniTrust National Mobility Interchange', 'NMI', 10, TRUE,
            'STATIC', 'mqtt.us.mobilityinterchange.com', 'tcp', 1883, FALSE,
            'IEEE1609_2_SIGNED_UPER', 'migration-010', 'migration-010')
    ON CONFLICT (provider_key) DO NOTHING
    RETURNING id INTO v_provider_id;

    IF v_provider_id IS NOT NULL THEN
        INSERT INTO mqtt_provider_acl_rules (provider_id, name, subscribe_limit, publish_rate_limit,
                created_by, updated_by)
        VALUES (v_provider_id, 'nmi.mobile.default', 36, 10, 'migration-010', 'migration-010')
        RETURNING id INTO v_rule_id;

        INSERT INTO mqtt_provider_acl_publish_topics (rule_id, topic_order, topic) VALUES
            (v_rule_id, 0, 'v1/g32/@/20|32');
        INSERT INTO mqtt_provider_acl_subscribe_topics (rule_id, topic_order, topic) VALUES
            (v_rule_id, 0, 'v1/g32/@/31|19|18|41');
    END IF;

    -- AV: {MSG}/unsigned/J2735/DDOT/{geohash}; TIM has no DDOT level. Six
    -- subscribe topics over a 3x3 grid = 54 subscriptions. Seeded disabled until
    -- an admin sets the REST base URL and credentials.
    v_provider_id := NULL;
    INSERT INTO mqtt_providers (provider_key, display_name, provider_type, priority, enabled,
            connection_source, mqtt_host, mqtt_protocol, mqtt_port, tls_enabled, mqtt_auth_type,
            payload_format, rest_auth_type, created_by, updated_by)
    VALUES ('av', 'AV', 'AV', 20, FALSE,
            'STATIC', 'v2x.aitoms.net', 'tcp', 1883, FALSE, 'USERNAME_PASSWORD',
            'UNSIGNED_UPER', 'BASIC', 'migration-010', 'migration-010')
    ON CONFLICT (provider_key) DO NOTHING
    RETURNING id INTO v_provider_id;

    IF v_provider_id IS NOT NULL THEN
        INSERT INTO mqtt_provider_rest_endpoints (provider_id, name, path) VALUES
            (v_provider_id, 'tim', '/tims');

        INSERT INTO mqtt_provider_acl_rules (provider_id, name, subscribe_limit, publish_rate_limit,
                created_by, updated_by)
        VALUES (v_provider_id, 'av.mobile.default', 54, 10, 'migration-010', 'migration-010')
        RETURNING id INTO v_rule_id;

        INSERT INTO mqtt_provider_acl_publish_topics (rule_id, topic_order, topic) VALUES
            (v_rule_id, 0, 'PSM/unsigned/J2735/DDOT/@'),
            (v_rule_id, 1, 'BSM/unsigned/J2735/DDOT/@');
        INSERT INTO mqtt_provider_acl_subscribe_topics (rule_id, topic_order, topic) VALUES
            (v_rule_id, 0, 'PSM/unsigned/J2735/DDOT/@'),
            (v_rule_id, 1, 'BSM/unsigned/J2735/DDOT/@'),
            (v_rule_id, 2, 'SDSM/unsigned/J2735/DDOT/@'),
            (v_rule_id, 3, 'SPAT/unsigned/J2735/DDOT/@'),
            (v_rule_id, 4, 'MAP/unsigned/J2735/DDOT/@'),
            (v_rule_id, 5, 'TIM/unsigned/J2735/@');
    END IF;
END;
$$;

COMMIT;
