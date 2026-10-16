# Migrations

SQL scripts that bring an existing PostgreSQL database up to the schema the API
expects.

## When to apply migrations

- **New databases** need no migrations for 001–008.
  [`../init-db.sql`](../init-db.sql) already contains everything from 001–008,
  plus the `error_logs` table, which has no migration. Docker runs `init-db.sql`
  only when the PostgreSQL volume is first initialized.
- **Existing databases** need every migration that was added after the database
  was created. Apply them before restarting the API on the new version.
  Hibernate's `ddl-auto=update` adds missing tables and columns, but it does not
  create the views, functions and triggers below, and it does not remove obsolete
  constraints.

## Applying a migration

Apply migrations in numeric order, except for 002 and 003 (see
[002](#002_add_user_limitssql)). Use `ON_ERROR_STOP` so that a failing statement
stops the script. Use `--single-transaction` to roll back a partially applied
script. 001–008 do not manage their own transactions.

```bash
MIGRATION=resources/postgres/migrations/007_set_existing_msg_type.sql
docker compose exec -T postgres sh -c \
  'exec psql -v ON_ERROR_STOP=1 --single-transaction -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < "$MIGRATION"
```

## Summary

| #   | Script | Adds or changes | Safe to re-run |
| --- | ------ | --------------- | -------------- |
| 001 | `001_add_registration_logs.sql` | `registration_logs` table | Yes |
| 002 | `002_add_user_limits.sql` | `user_limits` table, `update_updated_at_column()` | No: trigger already exists |
| 003 | `003_add_vendor_limits.sql` | `vendor_limits` table | No: trigger already exists |
| 004 | `004_add_geofence_deployments.sql` | `geofence_deployments`, `geofence_geohashes`, lookup functions | No: trigger already exists |
| 005 | `005_add_path_configs.sql` | `paths`, `path_coordinates`, `path_timestamps` | No: trigger already exists |
| 006 | `006_add_geohash_view.sql` | `active_geofence_geohashes` view and functions | Yes |
| 007 | `007_set_existing_msg_type.sql` | `message_types` table, `geofence_deployments.msg_type` | Yes |
| 008 | `008_setup_listen_notify.sql` | `table_updates` notification triggers | Yes |
| 009 | `009_user_limits_username_vendor_uniqueness.sql` | `(username, vendor_id)` uniqueness on `user_limits` | Yes (transactional) |

"No" means that a second run fails on a plain `CREATE TRIGGER`. The tables and
indexes use `IF NOT EXISTS`, so before you re-run one of these scripts, check
which of its triggers already exist.

## 001_add_registration_logs.sql

Creates `registration_logs`. It records each ETX device registration: device,
client type and subtype, vendor, requesting user, registration count, expiry,
active flag, and unregister and last-connected times. Single-column indexes
support the device, vendor, user, active, created and last-connected lookups.
Composite indexes on `(requested_by | vendor_id, is_active)` and
`(…, last_connected, is_active)` support the per-user and per-vendor limit checks.

## 002_add_user_limits.sql

Creates `user_limits`. It holds a per-user registration limit for each vendor,
with `UNIQUE (username, vendor_id)`. It also creates the shared trigger function
`update_updated_at_column()` and the `update_user_limits_updated_at` trigger.

**Ordering:** 002 and 003 depend on each other.
- `user_limits` has a foreign key to `vendor_limits(vendor_id)`, which 003 creates.
- 003's trigger uses `update_updated_at_column()`, which 002 creates.

Neither script succeeds on an empty database when run first. To apply them by hand:

1. Create `update_updated_at_column()` by running its `CREATE OR REPLACE FUNCTION`
   statement from 002.
2. Apply 003.
3. Apply 002.

The header comments in these files say "Migration 003" and "Migration 004"; go by
the file names.

## 003_add_vendor_limits.sql

Creates `vendor_limits`. It holds the vendor-wide registration limit, with
`vendor_id` unique and non-blank and `max_registrations > 0`. It also adds the
`update_vendor_limits_updated_at` trigger. See [002](#002_add_user_limitssql)
for the order in which to apply 002 and 003.

## 004_add_geofence_deployments.sql

Creates the V2X message deployment tables:

- `geofence_deployments`: one row per deployed TIM or MAP, with the GeoJSON
  region, the hex payload, who deployed it, an active flag and an optional
  `expires_at`. Includes a GIN index on `geojson` and an `updated_at` trigger.
- `geofence_geohashes`: the geohashes for each deployment. Rows cascade-delete
  with their deployment, and `(geofence_deployment_id, geohash)` is unique.

Also adds the helper functions `deactivate_geofence_deployment(geofence_id)`,
`get_geofence_deployments_by_geohash(geohash)` and
`get_geofence_geohashes(geofence_id)`. The API uses `get_geofence_geohashes` to
leave a deployment's own cells out of collision checks when that deployment is
updated.

`msg_type` is added later, by 007.

## 005_add_path_configs.sql

Creates `paths`, a named path with a GeoJSON feature type and geometry type,
and its child tables `path_coordinates` and `path_timestamps`. Both child tables
cascade-delete with their path. Also adds indexes and the
`update_paths_updated_at` trigger. This file has no header comment.

## 006_add_geohash_view.sql

Adds helpers for detecting geohash collisions during deployment:

- View `active_geofence_geohashes`: the distinct geohashes of deployments that
  are active and not expired (`expires_at` is null or later than
  `CURRENT_TIMESTAMP`). The API reads this view to avoid geohashes already
  claimed by another deployment.
- Functions `get_active_geohashes()`, `is_geohash_active(geohash)`,
  `get_active_geohashes_by_prefix(prefix)` and
  `get_active_geohashes_for_list(geohashes[])`.
- Index `idx_geofence_deployments_active_expires` on `(is_active, expires_at)`.

## 007_set_existing_msg_type.sql

Adds message types to deployments:

1. Creates the `message_types` lookup table and seeds `TIM` and `MAP` with their
   ASN.1 message-frame class names. The API maps a decoded frame to its code
   through `asn_class`.
2. Adds `geofence_deployments.msg_type`, **sets every existing row with no
   `msg_type` to `TIM`**, then makes the column `NOT NULL`.
3. Adds the foreign key `fk_geofence_deployments_msg_type` to
   `message_types(code)` if it is missing, and the index
   `idx_geofence_deployments_msg_type`.

If the database already holds MAP deployments with no `msg_type`, correct them
after applying this migration.

## 008_setup_listen_notify.sql

Creates `notify_table_update()` and `AFTER INSERT OR UPDATE OR DELETE` row
triggers on `geofence_deployments` and `geofence_geohashes`. These triggers
publish `<table>:<operation>` on the `table_updates` channel. The
[kafka-producer](../../../kafka-producer/README.md) listens on that channel to
reload its cache. It has no scheduled refresh, so without these triggers it
keeps serving stale deployments until it is restarted or refreshed by hand. The
script drops the triggers before
creating them, so it is safe to re-run.

## 009_user_limits_username_vendor_uniqueness.sql

Needed for databases where Hibernate created `user_limits` with a globally
unique `username`. That schema prevents one user from holding limits for more
than one vendor. The script:

- drops every UNIQUE constraint that covers only `username`, whatever its
  generated name, and keeps primary keys and other unique constraints;
- adds `uk_user_limits_username_vendor UNIQUE (username, vendor_id)` unless an
  equivalent constraint already exists.

Uniqueness also applies to inactive (soft-deleted) rows. The API therefore
updates or reactivates an existing row instead of inserting a duplicate. The
script locks `user_limits`, runs in its own transaction, does nothing when the
table does not exist, and is safe to re-run. On a database created from
`init-db.sql` or migration 002 it changes nothing, because those already define
the composite constraint.

Apply it before restarting the API with the user-limit changes:

```bash
docker compose exec -T postgres sh -c 'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < resources/postgres/migrations/009_user_limits_username_vendor_uniqueness.sql
```

### Test

[`tests/009_user_limits_username_vendor_uniqueness_test.sql`](tests/009_user_limits_username_vendor_uniqueness_test.sql)
applies the migration twice to a temporary `user_limits` table. It then checks
that:

- legacy constraints are dropped, including ones whose names need quoting;
- existing composite and unrelated unique constraints are kept;
- one user can hold rows for several vendors, while a duplicate user/vendor
  pair is rejected.

Temporary tables shadow any real tables for that connection only:

```bash
psql -X -v ON_ERROR_STOP=1 --dbname=test_database \
  -f resources/postgres/migrations/tests/009_user_limits_username_vendor_uniqueness_test.sql
```
