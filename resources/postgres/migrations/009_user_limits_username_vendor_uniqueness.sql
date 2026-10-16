-- Upgrade Hibernate-managed schemas that still require globally unique usernames.
-- Keep uniqueness on (username, vendor_id), including inactive rows.
BEGIN;

DO $$
DECLARE
    user_limits_table REGCLASS := to_regclass('user_limits');
    username_attribute SMALLINT;
    vendor_attribute SMALLINT;
    legacy_constraint RECORD;
BEGIN
    IF user_limits_table IS NULL THEN
        RETURN;
    END IF;

    -- Serialize the constraint replacement, including concurrent migration runs.
    EXECUTE format('LOCK TABLE %s IN ACCESS EXCLUSIVE MODE', user_limits_table);

    SELECT attnum INTO STRICT username_attribute
    FROM pg_attribute
    WHERE attrelid = user_limits_table AND attname = 'username' AND NOT attisdropped;

    SELECT attnum INTO STRICT vendor_attribute
    FROM pg_attribute
    WHERE attrelid = user_limits_table AND attname = 'vendor_id' AND NOT attisdropped;

    -- Hibernate-generated constraint names vary. Identify only username-only
    -- UNIQUE constraints by their columns; preserve primary keys and other keys.
    FOR legacy_constraint IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = user_limits_table
            AND contype = 'u'
            AND conkey = ARRAY[username_attribute]
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I',
                user_limits_table, legacy_constraint.conname);
    END LOOP;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = user_limits_table AND contype = 'u'
            AND (conkey = ARRAY[username_attribute, vendor_attribute]
                OR conkey = ARRAY[vendor_attribute, username_attribute])
    ) THEN
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I UNIQUE (username, vendor_id)',
                user_limits_table, 'uk_user_limits_username_vendor');
    END IF;
END;
$$;

COMMIT;
