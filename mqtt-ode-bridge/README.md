# MQTT → ODE bridge

Subscribes to J2735 topics on an MQTT broker and forwards every message as one UDP
datagram to the matching [USDOT JPO ODE](https://github.com/usdot-jpo-ode/jpo-ode) UDP
receiver, so broker traffic flows through the normal ODE → Kafka pipeline
(`topic.OdeMapJson`, `topic.OdeSpatJson`, `topic.OdeBsmJson`, …).

```
MQTT broker ──(MAP/SPAT/BSM/PSM/TIM topics)──> mqtt-ode-bridge ──(UDP, raw UPER)──> ODE receivers
```

## How it works

For each MQTT message:

1. **Retained messages are skipped** (`FORWARD_RETAINED=false`). The broker replays them on
   every (re)connect, which would re-inject stale data into the ODE.
2. **Message type** comes from the first topic level: `MAP/unsigned/J2735/DDOT/2062` → `MAP`.
3. **Payload is normalized to bytes.** The ODE needs raw UPER bytes; publishers that send
   ASCII hex text (on the DDOT broker, BSM and PSM do) are converted with `bytes.fromhex`.
   A raw J2735 frame starts with `0x00`, so it is never mistaken for hex text.
4. **Validated like the ODE does it.** The payload must contain the type's start flag
   (`0012` MAP, `0013` SPaT, `0014` BSM, `0020` PSM, `001f` TIM, `001d` SRM, `001e` SSM)
   at byte 0, or after a header from byte 2 on. It must not look like a different type, and
   it must fit the ODE receiver buffer (the ODE silently truncates bigger datagrams).
   Failures are dropped and counted as `no_start_flag`, `type_mismatch`, `oversize` or
   `unknown_type`.
5. **Routed** to `ODE_HOST` on that type's receiver port, to the generic port, or to a
   per-topic override (see [Port-mapped ingest](#port-mapped-ingest)).
6. **Sent** on one long-lived UDP socket. Send errors are counted, never fatal.

Every `STATS_INTERVAL` seconds it logs one line with counts per type and outcome:

```
stats 44981 msgs in 30s (1499.2/s): BSM sent=23149 | MAP sent=29 | PSM sent=21473 | SPAT sent=287 | TIM sent=43
```

## Running

```bash
# in .env (see sample.env): MQTT_HOST, MQTT_USERNAME, MQTT_PASSWORD, ODE_HOST, ...
docker compose --profile mqtt-ode-bridge up -d --build mqtt-ode-bridge
docker compose logs -f mqtt-ode-bridge
```

The service is deliberately **not** in the `all` profile, so existing deployments don't
start it without broker credentials.

Try it without touching the ODE first. `DRY_RUN` validates and counts everything but
sends nothing:

```bash
MQTT_ODE_BRIDGE_DRY_RUN=true docker compose --profile mqtt-ode-bridge up --build mqtt-ode-bridge
```

Without Docker:

```bash
pip install -r mqtt-ode-bridge/requirements.txt
MQTT_HOST=v2x.aitoms.net MQTT_USERNAME=... MQTT_PASSWORD=... ODE_HOST=ode.example \
    python mqtt-ode-bridge/mqtt_ode_bridge.py
```

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `MQTT_HOST` | — (required) | Broker host |
| `MQTT_PORT` | `1883` | |
| `MQTT_USERNAME` / `MQTT_PASSWORD` | — | Or `MQTT_PASSWORD_FILE` (e.g. a Docker secret). Never logged. |
| `MQTT_TLS` / `MQTT_TLS_INSECURE` | `false` | `INSECURE` skips certificate verification |
| `MQTT_CLIENT_ID` | broker-assigned | Use a unique ID per instance if you set one |
| `MQTT_TOPICS` | `MAP,SPAT,BSM,PSM,TIM` `/unsigned/J2735/#` | Comma-separated filters. Remove one to stop forwarding that type. |
| `MQTT_QOS` | `0` | |
| `ODE_HOST` | — (required unless `DRY_RUN`) | ODE host name or IP |
| `ODE_MAP_PORT` | `44920` | ODE 6.0.0 defaults (`ode.receivers.*` in the ODE `application.yaml`) |
| `ODE_SPAT_PORT` | `44910` | |
| `ODE_BSM_PORT` | `46800` | |
| `ODE_PSM_PORT` | `44940` | |
| `ODE_TIM_PORT` | `47900` | |
| `ODE_SRM_PORT` / `ODE_SSM_PORT` | `44930` / `44900` | If SRM/SSM topics are added to `MQTT_TOPICS` |
| `ODE_<TYPE>_BUFFER_SIZE` | MAP/TIM 2048, others 500 | Match the ODE's `buffer-size` if it was changed |
| `ODE_USE_GENERIC_PORT` | `false` | `true`: send everything to the generic receiver (type auto-detected) |
| `ODE_GENERIC_PORT` / `ODE_GENERIC_BUFFER_SIZE` | `44990` / `2000` | |
| `ODE_ROUTES` | — | Per-topic overrides, see below |
| `FORWARD_RETAINED` | `false` | |
| `DRY_RUN` | `false` | Compose variable: `MQTT_ODE_BRIDGE_DRY_RUN` |
| `STATS_INTERVAL` | `60` | Seconds. Compose variable: `MQTT_ODE_BRIDGE_STATS_INTERVAL` |
| `LOG_LEVEL` | `INFO` | `DEBUG` logs every dropped message. Compose variable: `MQTT_ODE_BRIDGE_LOG_LEVEL` |

The ODE's UDP ports must be reachable from the bridge. If the ODE is on another host,
open the ports (UDP) in its firewall and Docker port mappings.

## Port-mapped ingest

The ODE records the UDP sender as `metadata.originIp`, so by default every message looks
like it came from the bridge. ODE 6.0.0+ can instead map a dedicated port to an
intersection (`ode.port-mapped-ingest.sources[]`, with `intersection-id`, `origin-ip`,
`port` and `type`). Point topics at those ports with `ODE_ROUTES`:

```
ODE_ROUTES=MAP/unsigned/J2735/DDOT/2062=15003,SPAT/unsigned/J2735/DDOT/2062=16003
```

Entries are `topic-filter=port` (uses `ODE_HOST`) or `topic-filter=host:port`. Filters
support MQTT wildcards (`+`, `#`). The first matching entry wins, then the generic or
per-type port applies.

## Health and failures

- **Health:** the container is healthy while connected to the broker (a heartbeat file is
  touched every 10 s; `python mqtt_ode_bridge.py --healthcheck`).
- **Broker drops:** reconnects automatically (1–30 s backoff) and re-subscribes.
- **Bad credentials** (`Not authorized` / `Bad user name or password`): exits with code 2,
  so it shows up in `docker compose ps` and the restart policy.
- **SIGTERM / Ctrl+C:** disconnects cleanly and logs the totals.

## Verifying ingestion on the ODE

Watch the ODE's output topics (any ODE version):

```bash
kafkacat -b <kafka-host>:9092 -C -t topic.OdeMapJson -c 1
kafkacat -b <kafka-host>:9092 -C -t topic.OdeSpatJson -c 1
kafkacat -b <kafka-host>:9092 -C -t topic.OdeBsmJson -c 1
```

Check `metadata.recordType` (`mapTx`, `spatTx`, `bsmTx`, `psmTx`, `timMsg`) and
`metadata.originIp`. On upstream ODE 6.0.0, `topic.OdeRawEncoded<TYPE>Json` shows the
UDP step alone. ODE log lines with `InvalidPayloadException` mean a payload was rejected.

## Load

On the DDOT broker (`v2x.aitoms.net`, Nov 2026) the bridge forwards about
**1,450 messages/s**: ~770 BSM/s, ~700 PSM/s, ~10 SPaT/s, ~1 MAP/s and a few TIMs. Make
sure the ODE and its Kafka can absorb that, or drop BSM/PSM from `MQTT_TOPICS`.

## Tests

```bash
pip install -r mqtt-ode-bridge/requirements-dev.txt
python -m pytest mqtt-ode-bridge/tests
```
