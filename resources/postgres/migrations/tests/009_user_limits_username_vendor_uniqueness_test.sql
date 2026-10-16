\set ON_ERROR_STOP on

-- Temporary tables shadow any application tables for this connection only.
CREATE TEMP TABLE user_limits (
    id INTEGER PRIMARY KEY,
    username TEXT NOT NULL,
    vendor_id TEXT NOT NULL,
    reference_code TEXT UNIQUE,
    CONSTRAINT legacy_username_unique UNIQUE (username),
    CONSTRAINT "Legacy Username With Spaces" UNIQUE (username)
);

\ir ../009_user_limits_username_vendor_uniqueness.sql
\ir ../009_user_limits_username_vendor_uniqueness.sql

INSERT INTO user_limits VALUES (1, 'user', 'vendor-a', 'ref-a'), (2, 'user', 'vendor-b', 'ref-b');

DO $$
BEGIN
    BEGIN
        INSERT INTO user_limits VALUES (3, 'user', 'vendor-a', 'ref-c');
        RAISE EXCEPTION 'Duplicate username/vendor pair was accepted';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;

    BEGIN
        INSERT INTO user_limits VALUES (4, 'other', 'vendor-c', 'ref-a');
        RAISE EXCEPTION 'Unrelated reference-code unique constraint was removed';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;

    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'user_limits'::regclass
            AND conname IN ('legacy_username_unique', 'Legacy Username With Spaces')
    ) THEN
        RAISE EXCEPTION 'Legacy username-only constraint survived migration';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'user_limits'::regclass
            AND conname = 'uk_user_limits_username_vendor' AND contype = 'u'
    ) THEN
        RAISE EXCEPTION 'Composite unique constraint was not installed';
    END IF;
END;
$$;

DROP TABLE pg_temp.user_limits;

-- Preserve an existing equivalent composite constraint, including reversed order.
CREATE TEMP TABLE user_limits (
    id INTEGER PRIMARY KEY,
    username TEXT NOT NULL,
    vendor_id TEXT NOT NULL,
    CONSTRAINT another_legacy_username_unique UNIQUE (username),
    CONSTRAINT existing_vendor_username_unique UNIQUE (vendor_id, username)
);

\ir ../009_user_limits_username_vendor_uniqueness.sql
\ir ../009_user_limits_username_vendor_uniqueness.sql

INSERT INTO user_limits VALUES (1, 'user', 'vendor-a'), (2, 'user', 'vendor-b');

DO $$
BEGIN
    BEGIN
        INSERT INTO user_limits VALUES (3, 'user', 'vendor-a');
        RAISE EXCEPTION 'Existing composite constraint was removed';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;

    IF (SELECT COUNT(*) FROM pg_constraint
            WHERE conrelid = 'user_limits'::regclass AND contype = 'u') <> 1
            OR NOT EXISTS (SELECT 1 FROM pg_constraint
                WHERE conrelid = 'user_limits'::regclass
                    AND conname = 'existing_vendor_username_unique') THEN
        RAISE EXCEPTION 'Existing composite constraint was replaced or duplicated';
    END IF;
END;
$$;

DROP TABLE pg_temp.user_limits;
