# CV-MEC Kafka Producer

Sidecar service that reads active geofence payloads from PostgreSQL and publishes `GeoHashRoutedMsg` protobuf messages to Kafka (default 1 Hz, configurable). Cache invalidation uses PostgreSQL `LISTEN` on the `table_updates` channel (requires migration `008_setup_listen_notify.sql` or `init-db.sql` triggers).

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_SERVER_URL` | `jdbc:postgresql://localhost:5432` | JDBC base URL (no database name) |
| `POSTGRES_DB` | `v2x_app_db` | Database name |
| `POSTGRES_USER` | `admin_user` | Database user |
| `POSTGRES_PASSWORD` | _(empty)_ | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `SPRING_PROFILES_ACTIVE` | `default` | Set to `local` or `confluent` for profile-specific overrides. In Docker Compose, set via `KAFKA_PRODUCER_SPRING_PROFILES_ACTIVE` in `.env`. |
| `KAFKA_PRODUCER_PUBLISHING_THREAD_POOL_SIZE` | _(unset)_ | Fixed thread pool size for parallel Kafka publishing. When unset or `0`, defaults to `min(availableProcessors * 2, 20)`. Increase for higher ingestion volume. |
| `KAFKA_PRODUCER_PUBLISHING_FREQUENCY_HZ` | _(unset)_ | Kafka publishing frequency in Hz. When unset or `0`, defaults to `1` (one publish cycle per second). Examples: `2` for 500 ms intervals, `0.5` for 2 s intervals. |
| `KAFKA_PRODUCER_PUBLISHING_BATCH_TIMEOUT` | `5s` | Maximum time to wait for a publish cycle to finish. Supports Spring duration format (e.g. `5s`, `500ms`, `1m`). |

Topic name is configured under `kafka-producer.kafka.topics.geo-hash-routed-msg` (default: `topic.GeoHashRoutedMsg`).

The JPQL query that loads active geohash payloads is externalized under `kafka-producer.postgres.query.find-geohash-payloads` in `application.yaml`. Override at deploy time with `KAFKA_PRODUCER_POSTGRES_QUERY_FIND_GEOHASH_PAYLOADS` if entity fields or join conditions change, without rebuilding the service.

## Profiles

- **local** — `application-local.yaml` sets bootstrap servers to `localhost:9092`
- **confluent** — `application-confluent.yaml` enables SASL_SSL (set `CONFLUENT_KEY` and `CONFLUENT_SECRET`)

## Docker Compose

From the repository root (see root `sample.env`):

```bash
cp sample.env .env
# Set KAFKA_BOOTSTRAP_SERVERS to a broker reachable from Docker
COMPOSE_PROFILES=kafka-producer docker compose up -d postgres kafka-producer
```

## Build and run

```bash
cd kafka-producer
./gradlew bootRun
```

With the monorepo Postgres stack:

```bash
SPRING_PROFILES_ACTIVE=local \
POSTGRES_PASSWORD=change_me_123 \
./gradlew bootRun
```

## Tests

```bash
./gradlew test
```

Unit tests do not require a running Postgres or Kafka instance.
