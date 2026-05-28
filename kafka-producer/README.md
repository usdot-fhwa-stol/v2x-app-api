# CV-MEC Kafka Producer

Sidecar service that reads active geofence payloads from PostgreSQL and publishes `GeoHashRoutedMsg` protobuf messages to Kafka at 1 Hz. Cache invalidation uses PostgreSQL `LISTEN` on the `table_updates` channel (requires migration `008_setup_listen_notify.sql` or `init-db.sql` triggers).

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_SERVER_URL` | `jdbc:postgresql://localhost:5432` | JDBC base URL (no database name) |
| `POSTGRES_DB` | `v2x_app_db` | Database name |
| `POSTGRES_USER` | `admin_user` | Database user |
| `POSTGRES_PASSWORD` | _(empty)_ | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `SPRING_PROFILES_ACTIVE` | `default` | Set to `local` or `confluent` for profile-specific overrides. In Docker Compose, set via `KAFKA_PRODUCER_SPRING_PROFILES_ACTIVE` in `.env`. |

Topic name is configured under `kafka-producer.kafka.topics.geo-hash-routed-msg` (default: `topic.GeoHashRoutedMsg`).

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
