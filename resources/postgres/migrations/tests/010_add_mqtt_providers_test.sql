\set ON_ERROR_STOP on

-- Run against a throwaway database: migration 010 creates real tables and
-- commits its own transaction.
--   createdb mqtt_providers_test
--   psql -X -v ON_ERROR_STOP=1 --dbname=mqtt_providers_test \
--     -f resources/postgres/migrations/tests/010_add_mqtt_providers_test.sql
--   dropdb mqtt_providers_test

\ir ../010_add_mqtt_providers.sql
\ir ../010_add_mqtt_providers.sql

-- Seeds are created once and match the documented ACL rules.
DO $$
DECLARE
    actual TEXT;
BEGIN
    IF (SELECT COUNT(*) FROM mqtt_providers) <> 3 THEN
        RAISE EXCEPTION 'Expected 3 seeded providers';
    END IF;

    IF (SELECT enabled FROM mqtt_providers WHERE provider_key = 'av') THEN
        RAISE EXCEPTION 'AV must be seeded disabled';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM mqtt_providers
        WHERE provider_key = 'av' AND mqtt_host = 'v2x.aitoms.net' AND mqtt_port = 1883
            AND mqtt_protocol = 'tcp' AND mqtt_auth_type = 'USERNAME_PASSWORD'
            AND payload_format = 'UNSIGNED_UPER' AND rest_auth_type = 'BASIC'
    ) THEN
        RAISE EXCEPTION 'AV provider seed is wrong';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM mqtt_providers
        WHERE provider_key = 'nmi' AND enabled AND mqtt_host = 'mqtt.us.mobilityinterchange.com'
            AND mqtt_auth_type IS NULL AND payload_format = 'IEEE1609_2_SIGNED_UPER'
    ) THEN
        RAISE EXCEPTION 'NMI provider seed is wrong';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM mqtt_providers
        WHERE provider_key = 'etx' AND connection_source = 'ETX_CONNECTION'
            AND payload_format = 'GEO_ROUTED_PROTO_UNSIGNED_UPER'
    ) THEN
        RAISE EXCEPTION 'ETX provider seed is wrong';
    END IF;

    SELECT string_agg(t.topic, ',' ORDER BY t.topic_order) INTO actual
    FROM mqtt_provider_acl_subscribe_topics t
    JOIN mqtt_provider_acl_rules r ON r.id = t.rule_id
    WHERE r.name = 'av.mobile.default';
    IF actual IS DISTINCT FROM 'PSM/unsigned/J2735/DDOT/@,BSM/unsigned/J2735/DDOT/@,SDSM/unsigned/J2735/DDOT/@,'
            || 'SPAT/unsigned/J2735/DDOT/@,MAP/unsigned/J2735/DDOT/@,TIM/unsigned/J2735/@' THEN
        RAISE EXCEPTION 'AV subscribe topics are wrong: %', actual;
    END IF;

    SELECT string_agg(t.topic, ',' ORDER BY t.topic_order) INTO actual
    FROM mqtt_provider_acl_publish_topics t
    JOIN mqtt_provider_acl_rules r ON r.id = t.rule_id
    WHERE r.name = 'av.mobile.default';
    IF actual IS DISTINCT FROM 'PSM/unsigned/J2735/DDOT/@,BSM/unsigned/J2735/DDOT/@' THEN
        RAISE EXCEPTION 'AV publish topics are wrong: %', actual;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM mqtt_provider_acl_rules r
        JOIN mqtt_provider_acl_subscribe_topics s ON s.rule_id = r.id
        JOIN mqtt_provider_acl_publish_topics p ON p.rule_id = r.id
        WHERE r.name = 'nmi.mobile.default' AND r.subscribe_limit = 36
            AND r.geohash_symbol = '@' AND r.geohash_levels = 7
            AND r.publish_precision = 7 AND r.subscribe_precision = 7 AND r.subscribe_grid_radius = 1
            AND s.topic = 'v1/g32/@/31|19|18|41' AND p.topic = 'v1/g32/@/20|32'
    ) THEN
        RAISE EXCEPTION 'NMI ACL rule seed is wrong';
    END IF;

    IF (SELECT subscribe_limit FROM mqtt_provider_acl_rules WHERE name = 'av.mobile.default') <> 54 THEN
        RAISE EXCEPTION 'AV subscribe limit should be 54';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM mqtt_provider_rest_endpoints e
            JOIN mqtt_providers p ON p.id = e.provider_id
            WHERE p.provider_key = 'av' AND e.name = 'tim' AND e.path = '/tims') THEN
        RAISE EXCEPTION 'AV TIM REST endpoint missing';
    END IF;

    IF (SELECT message_id FROM j2735_message_ids WHERE code = 'SDSM') <> 41 THEN
        RAISE EXCEPTION 'SDSM must map to messageId 41';
    END IF;
    IF (SELECT COUNT(*) FROM j2735_message_ids) <> 6 THEN
        RAISE EXCEPTION 'Expected 6 J2735 message ids';
    END IF;
END;
$$;

-- Re-running never overwrites or resurrects admin changes.
UPDATE mqtt_providers SET mqtt_host = 'mqtt.development.v2x.isscms.com' WHERE provider_key = 'nmi';
DELETE FROM mqtt_provider_acl_subscribe_topics t
USING mqtt_provider_acl_rules r
WHERE r.id = t.rule_id AND r.name = 'av.mobile.default' AND t.topic_order = 5;
DELETE FROM mqtt_provider_rest_endpoints;
UPDATE j2735_message_ids SET description = 'edited' WHERE code = 'BSM';

\ir ../010_add_mqtt_providers.sql

DO $$
BEGIN
    IF (SELECT mqtt_host FROM mqtt_providers WHERE provider_key = 'nmi') <> 'mqtt.development.v2x.isscms.com' THEN
        RAISE EXCEPTION 'Re-run overwrote the NMI host';
    END IF;
    IF (SELECT COUNT(*) FROM mqtt_provider_acl_subscribe_topics t
            JOIN mqtt_provider_acl_rules r ON r.id = t.rule_id
            WHERE r.name = 'av.mobile.default') <> 5 THEN
        RAISE EXCEPTION 'Re-run resurrected a deleted AV subscribe topic';
    END IF;
    IF EXISTS (SELECT 1 FROM mqtt_provider_rest_endpoints) THEN
        RAISE EXCEPTION 'Re-run resurrected a deleted REST endpoint';
    END IF;
    IF (SELECT description FROM j2735_message_ids WHERE code = 'BSM') <> 'edited' THEN
        RAISE EXCEPTION 'Re-run overwrote a message id row';
    END IF;
END;
$$;

-- updated_at trigger fires (each psql statement is its own transaction, so
-- CURRENT_TIMESTAMP advances past created_at).
UPDATE mqtt_providers SET display_name = 'ETX' WHERE provider_key = 'etx';
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM mqtt_providers WHERE provider_key = 'etx' AND updated_at > created_at) THEN
        RAISE EXCEPTION 'updated_at trigger did not fire';
    END IF;
END;
$$;

-- Constraints reject invalid rows.
DO $$
BEGIN
    BEGIN
        INSERT INTO mqtt_providers (provider_key, display_name, provider_type, payload_format)
        VALUES ('static-no-host', 'X', 'GENERIC', 'UNSIGNED_UPER');
        RAISE EXCEPTION 'Enabled STATIC provider without host was accepted';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;

    BEGIN
        INSERT INTO mqtt_providers (provider_key, display_name, provider_type, payload_format, enabled)
        VALUES ('Bad Key', 'X', 'GENERIC', 'UNSIGNED_UPER', FALSE);
        RAISE EXCEPTION 'Invalid provider_key was accepted';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;

    BEGIN
        INSERT INTO mqtt_providers (provider_key, display_name, provider_type, payload_format,
                mqtt_host, mqtt_protocol, mqtt_port)
        VALUES ('bad-port', 'X', 'GENERIC', 'UNSIGNED_UPER', 'h', 'tcp', 70000);
        RAISE EXCEPTION 'Invalid port was accepted';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;

    BEGIN
        INSERT INTO mqtt_provider_acl_rules (provider_id, name, subscribe_precision)
        SELECT id, 'too-precise', 8 FROM mqtt_providers WHERE provider_key = 'nmi';
        RAISE EXCEPTION 'subscribe_precision above geohash_levels was accepted';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;

    BEGIN
        INSERT INTO mqtt_provider_acl_rules (provider_id, name, subscribe_grid_radius)
        SELECT id, 'too-wide', 3 FROM mqtt_providers WHERE provider_key = 'nmi';
        RAISE EXCEPTION 'subscribe_grid_radius 3 was accepted';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;

    BEGIN
        INSERT INTO mqtt_provider_acl_rules (provider_id, name)
        SELECT id, 'nmi.mobile.default' FROM mqtt_providers WHERE provider_key = 'nmi';
        RAISE EXCEPTION 'Duplicate rule name was accepted';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;

    BEGIN
        INSERT INTO mqtt_provider_credentials (provider_id)
        SELECT id FROM mqtt_providers WHERE provider_key = 'av';
        INSERT INTO mqtt_provider_credentials (provider_id)
        SELECT id FROM mqtt_providers WHERE provider_key = 'av';
        RAISE EXCEPTION 'Second credential set for one provider was accepted';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;
END;
$$;

-- Deleting a provider cascades to its children.
DELETE FROM mqtt_providers WHERE provider_key = 'av';
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM mqtt_provider_acl_rules WHERE name = 'av.mobile.default') THEN
        RAISE EXCEPTION 'AV rules survived provider delete';
    END IF;
    IF (SELECT COUNT(*) FROM mqtt_provider_acl_subscribe_topics) <> 1 THEN
        RAISE EXCEPTION 'AV topics survived provider delete';
    END IF;
END;
$$;

\echo '010_add_mqtt_providers_test: OK'
