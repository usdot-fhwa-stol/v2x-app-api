#!/usr/bin/env python3
"""MQTT -> ODE UDP bridge.

Subscribes to J2735 topics on an MQTT broker (MAP, SPaT, BSM, PSM, TIM, ...) and
forwards every message as one UDP datagram to the matching USDOT JPO ODE UDP
receiver.

The ODE expects raw UPER bytes (it searches the datagram for the 2-byte
MessageFrame start flag, e.g. 0x0012 for MAP), so payloads that the broker
publishes as ASCII hex text (BSM/PSM on the DDOT broker) are converted to bytes
first. Payloads without the expected start flag, or larger than the ODE
receiver's buffer, are dropped and counted rather than sent.

All configuration comes from environment variables; see README.md.

Usage:
    python mqtt_ode_bridge.py               # run the bridge
    python mqtt_ode_bridge.py --healthcheck # exit 0 if the heartbeat is fresh (Docker HEALTHCHECK)
"""

import logging
import os
import signal
import socket
import ssl
import sys
import threading
import time
from collections import Counter
from dataclasses import dataclass, field

import paho.mqtt.client as mqtt

log = logging.getLogger("mqtt-ode-bridge")


@dataclass(frozen=True)
class MessageType:
    name: str
    start_flag: bytes  # 2-byte J2735 MessageFrame messageId
    port: int  # ODE 6.0.0 default receiver port
    buffer_size: int  # ODE receiver buffer; larger datagrams are truncated by the ODE


# ODE 6.0.0 defaults: jpo-ode-svcs/src/main/resources/application.yaml (ode.receivers.*)
# and uper/SupportedMessageType.java.
MESSAGE_TYPES = {
    t.name: t
    for t in (
        MessageType("MAP", b"\x00\x12", 44920, 2048),
        MessageType("SPAT", b"\x00\x13", 44910, 500),
        MessageType("BSM", b"\x00\x14", 46800, 500),
        MessageType("SRM", b"\x00\x1d", 44930, 500),
        MessageType("SSM", b"\x00\x1e", 44900, 500),
        MessageType("TIM", b"\x00\x1f", 47900, 2048),
        MessageType("PSM", b"\x00\x20", 44940, 500),
    )
}
GENERIC_PORT = 44990
GENERIC_BUFFER_SIZE = 2000

DEFAULT_TOPICS = ",".join(
    f"{t}/unsigned/J2735/#" for t in ("MAP", "SPAT", "BSM", "PSM", "TIM")
)
HEARTBEAT_INTERVAL = 10  # seconds between heartbeat touches while connected
HEARTBEAT_MAX_AGE = 3 * HEARTBEAT_INTERVAL
FATAL_CONNECT_REASONS = ("not authorized", "bad user name or password", "banned")

HEX_DIGITS = b"0123456789abcdefABCDEF"


# --------------------------------------------------------------------------- payload helpers


def normalize_payload(payload: bytes) -> bytes:
    """Return the UPER bytes of an MQTT payload.

    Some publishers send the UPER encoding as ASCII hex text (b"001425...") instead
    of raw bytes. A raw J2735 MessageFrame starts with a 0x00 byte, so it can never
    be mistaken for hex text.
    """
    text = payload.strip()
    if text and len(text) % 2 == 0 and not text.translate(None, HEX_DIGITS):
        return bytes.fromhex(text.decode("ascii"))
    return payload


def find_start_flag(data: bytes, flag: bytes) -> int:
    """Byte offset of a start flag using the ODE's rules, or -1.

    Mirrors UperUtil.findValidStartFlagLocation(byte[], byte[]): a hit at offset 0
    is accepted; otherwise the search starts at byte 2 so a header prefix isn't
    misread as a start flag.
    """
    index = data.find(flag)
    if index in (0, -1):
        return index
    return data.find(flag, 2)


def detect_type(data: bytes):
    """Message type whose start flag appears earliest (ODE generic receiver logic), or None."""
    best, best_index = None, None
    for mtype in MESSAGE_TYPES.values():
        index = find_start_flag(data, mtype.start_flag)
        if index != -1 and (best_index is None or index < best_index):
            best, best_index = mtype.name, index
    return best


def topic_type(topic: str) -> str:
    """MAP/unsigned/J2735/DDOT/2062 -> MAP."""
    return topic.split("/", 1)[0].upper()


def classify(topic: str, payload: bytes, buffer_sizes: dict):
    """Decide what to do with a message.

    Returns (outcome, type name, normalized bytes). outcome is "send" or a drop
    reason: "unknown_type", "no_start_flag", "type_mismatch", "oversize".
    """
    name = topic_type(topic)
    mtype = MESSAGE_TYPES.get(name)
    if mtype is None:
        return "unknown_type", name, payload
    data = normalize_payload(payload)
    if find_start_flag(data, mtype.start_flag) == -1:
        return "no_start_flag", name, data
    detected = detect_type(data)
    if detected != name:
        # e.g. a BSM frame published on a MAP topic; the typed ODE receiver would mislabel it.
        return "type_mismatch", name, data
    if len(data) > buffer_sizes[name]:
        return "oversize", name, data
    return "send", name, data


# --------------------------------------------------------------------------- configuration


def env_bool(name: str, default: bool) -> bool:
    value = os.environ.get(name)
    if value is None or value.strip() == "":
        return default
    return value.strip().lower() in ("1", "true", "yes", "on")


def env_int(name: str, default: int) -> int:
    value = os.environ.get(name, "").strip()
    return int(value) if value else default


def parse_routes(spec: str, default_host: str):
    """Parse ODE_ROUTES: "topic-filter=host:port,topic-filter=port,...".

    Used for ODE port-mapped ingest, where each intersection/type has its own port.
    """
    routes = []
    for item in filter(None, (part.strip() for part in spec.split(","))):
        if "=" not in item:
            raise ValueError(f"ODE_ROUTES entry {item!r} must look like topic-filter=host:port")
        pattern, target = (part.strip() for part in item.rsplit("=", 1))
        host, _, port = target.rpartition(":")
        routes.append((pattern, host or default_host, int(port)))
    return routes


@dataclass
class Config:
    mqtt_host: str
    mqtt_port: int
    mqtt_username: str
    mqtt_password: str = field(repr=False)
    mqtt_tls: bool
    mqtt_tls_insecure: bool
    mqtt_client_id: str
    mqtt_topics: list
    mqtt_qos: int
    ode_host: str
    ports: dict
    buffer_sizes: dict
    use_generic_port: bool
    generic_port: int
    routes: list
    forward_retained: bool
    dry_run: bool
    stats_interval: int
    heartbeat_file: str

    @classmethod
    def from_env(cls):
        password = os.environ.get("MQTT_PASSWORD", "")
        password_file = os.environ.get("MQTT_PASSWORD_FILE", "").strip()
        if password_file:
            with open(password_file, encoding="utf-8") as fh:
                password = fh.read().strip()
        ode_host = os.environ.get("ODE_HOST", "").strip()
        generic_port = env_int("ODE_GENERIC_PORT", GENERIC_PORT)
        config = cls(
            mqtt_host=os.environ.get("MQTT_HOST", "").strip(),
            mqtt_port=env_int("MQTT_PORT", 1883),
            mqtt_username=os.environ.get("MQTT_USERNAME", ""),
            mqtt_password=password,
            mqtt_tls=env_bool("MQTT_TLS", False),
            mqtt_tls_insecure=env_bool("MQTT_TLS_INSECURE", False),
            mqtt_client_id=os.environ.get("MQTT_CLIENT_ID", ""),
            mqtt_topics=[
                t.strip() for t in os.environ.get("MQTT_TOPICS", DEFAULT_TOPICS).split(",") if t.strip()
            ],
            mqtt_qos=env_int("MQTT_QOS", 0),
            ode_host=ode_host,
            ports={n: env_int(f"ODE_{n}_PORT", t.port) for n, t in MESSAGE_TYPES.items()},
            buffer_sizes={
                n: env_int(f"ODE_{n}_BUFFER_SIZE", t.buffer_size) for n, t in MESSAGE_TYPES.items()
            },
            use_generic_port=env_bool("ODE_USE_GENERIC_PORT", False),
            generic_port=generic_port,
            routes=parse_routes(os.environ.get("ODE_ROUTES", ""), ode_host),
            forward_retained=env_bool("FORWARD_RETAINED", False),
            dry_run=env_bool("DRY_RUN", False),
            stats_interval=env_int("STATS_INTERVAL", 60),
            heartbeat_file=os.environ.get("HEARTBEAT_FILE", "/tmp/mqtt-ode-bridge.heartbeat"),
        )
        if config.use_generic_port:
            # Everything goes through one receiver, so its buffer is the limit.
            generic_buffer = env_int("ODE_GENERIC_BUFFER_SIZE", GENERIC_BUFFER_SIZE)
            config.buffer_sizes = {n: generic_buffer for n in config.buffer_sizes}
        return config

    def validate(self):
        problems = []
        if not self.mqtt_host:
            problems.append("MQTT_HOST is required")
        if not self.ode_host and not self.dry_run:
            problems.append("ODE_HOST is required (or set DRY_RUN=true)")
        if not self.mqtt_topics:
            problems.append("MQTT_TOPICS is empty")
        if self.mqtt_qos not in (0, 1, 2):
            problems.append("MQTT_QOS must be 0, 1 or 2")
        return problems


# --------------------------------------------------------------------------- routing / sending


class Router:
    """Maps a topic + message type to the ODE (host, port)."""

    def __init__(self, config: Config):
        self.config = config

    def route(self, topic: str, type_name: str):
        for pattern, host, port in self.config.routes:
            if mqtt.topic_matches_sub(pattern, topic):
                return host, port
        if self.config.use_generic_port:
            return self.config.ode_host, self.config.generic_port
        return self.config.ode_host, self.config.ports[type_name]


class UdpSender:
    """One long-lived UDP socket; destination addresses are resolved once and cached."""

    def __init__(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self._addresses = {}

    def _resolve(self, host, port):
        key = (host, port)
        if key not in self._addresses:
            info = socket.getaddrinfo(host, port, socket.AF_INET, socket.SOCK_DGRAM)
            self._addresses[key] = info[0][4]
        return self._addresses[key]

    def send(self, data: bytes, host: str, port: int):
        try:
            self.sock.sendto(data, self._resolve(host, port))
        except OSError:
            # Re-resolve next time (e.g. the ODE container was recreated with a new IP).
            self._addresses.pop((host, port), None)
            raise

    def close(self):
        self.sock.close()


# --------------------------------------------------------------------------- bridge


class Stats:
    """Thread-safe counters keyed by (type, outcome)."""

    def __init__(self):
        self._lock = threading.Lock()
        self._window = Counter()
        self.total = Counter()

    def add(self, type_name, outcome):
        with self._lock:
            self._window[(type_name, outcome)] += 1
            self.total[(type_name, outcome)] += 1

    def take_window(self):
        with self._lock:
            window, self._window = self._window, Counter()
        return window


def format_stats(counts: Counter, seconds: float) -> str:
    types = sorted({t for t, _ in counts})
    parts = []
    for name in types:
        # "sent" first, then "dry_run" (validated but not sent), then drop reasons.
        outcomes = {o: c for (t, o), c in counts.items() if t == name}
        order = sorted(outcomes, key=lambda o: (o != "sent", o != "dry_run", o))
        parts.append(f"{name} " + " ".join(f"{o}={outcomes[o]}" for o in order))
    total = sum(counts.values())
    rate = total / seconds if seconds > 0 else 0.0
    return f"{total} msgs in {seconds:.0f}s ({rate:.1f}/s): " + (" | ".join(parts) or "none")


class Bridge:
    def __init__(self, config: Config, sender=None):
        self.config = config
        self.router = Router(config)
        self.sender = sender or UdpSender()
        self.stats = Stats()
        self.seen_topics = set()
        self.stop_event = threading.Event()
        self.fatal_error = None
        self.client = None

    # Called from paho's network thread for every message.
    def handle_message(self, topic: str, payload: bytes, retain: bool):
        if topic not in self.seen_topics:
            self.seen_topics.add(topic)
            log.info("First message on %s (%d bytes, retained=%s)", topic, len(payload), retain)
        if retain and not self.config.forward_retained:
            self.stats.add(topic_type(topic), "skipped_retained")
            return
        outcome, type_name, data = classify(topic, payload, self.config.buffer_sizes)
        if outcome != "send":
            self.stats.add(type_name, outcome)
            log.debug("Dropped %s message on %s: %s", type_name, topic, outcome)
            return
        if self.config.dry_run:
            self.stats.add(type_name, "dry_run")
            return
        host, port = self.router.route(topic, type_name)
        try:
            self.sender.send(data, host, port)
            self.stats.add(type_name, "sent")
        except OSError as exc:
            self.stats.add(type_name, "send_error")
            log.warning("UDP send to %s:%d failed: %s", host, port, exc)

    def _on_connect(self, client, userdata, flags, reason_code, properties):
        if reason_code.is_failure:
            reason = str(reason_code)
            if any(r in reason.lower() for r in FATAL_CONNECT_REASONS):
                self.fatal_error = f"MQTT connect refused: {reason}"
                self.stop_event.set()
            else:
                log.warning("MQTT connect failed: %s (will retry)", reason)
            return
        log.info("Connected to %s:%d", self.config.mqtt_host, self.config.mqtt_port)
        # Subscribing here restores the subscriptions after every reconnect.
        client.subscribe([(topic, self.config.mqtt_qos) for topic in self.config.mqtt_topics])

    def _on_subscribe(self, client, userdata, mid, reason_codes, properties):
        for topic, rc in zip(self.config.mqtt_topics, reason_codes):
            if rc.is_failure:
                log.error("Subscription to %s rejected: %s", topic, rc)
            else:
                log.info("Subscribed to %s (%s)", topic, rc)

    def _on_disconnect(self, client, userdata, flags, reason_code, properties):
        if not self.stop_event.is_set():
            log.warning("Disconnected from broker (%s); reconnecting", reason_code)

    def _on_message(self, client, userdata, msg):
        try:
            self.handle_message(msg.topic, msg.payload, msg.retain)
        except Exception:  # never let one bad message kill the network thread
            log.exception("Error handling message on %s", msg.topic)

    def _make_client(self):
        cfg = self.config
        client = mqtt.Client(
            mqtt.CallbackAPIVersion.VERSION2, client_id=cfg.mqtt_client_id, protocol=mqtt.MQTTv311
        )
        if cfg.mqtt_username:
            client.username_pw_set(cfg.mqtt_username, cfg.mqtt_password)
        if cfg.mqtt_tls:
            client.tls_set(cert_reqs=ssl.CERT_NONE if cfg.mqtt_tls_insecure else ssl.CERT_REQUIRED)
            client.tls_insecure_set(cfg.mqtt_tls_insecure)
        client.on_connect = self._on_connect
        client.on_subscribe = self._on_subscribe
        client.on_disconnect = self._on_disconnect
        client.on_message = self._on_message
        client.reconnect_delay_set(min_delay=1, max_delay=30)
        return client

    def _touch_heartbeat(self):
        try:
            with open(self.config.heartbeat_file, "w", encoding="ascii") as fh:
                fh.write(str(time.time()))
        except OSError as exc:
            log.warning("Could not write heartbeat file: %s", exc)

    def run(self) -> int:
        cfg = self.config
        log.info(
            "Bridging %s -> %s (%s)%s",
            ", ".join(cfg.mqtt_topics),
            cfg.ode_host or "(none)",
            f"generic port {cfg.generic_port}"
            if cfg.use_generic_port
            else ", ".join(f"{n}:{cfg.ports[n]}" for n in ("MAP", "SPAT", "BSM", "PSM", "TIM")),
            " [DRY RUN - nothing is sent]" if cfg.dry_run else "",
        )
        for pattern, host, port in cfg.routes:
            log.info("Route override: %s -> %s:%d", pattern, host, port)

        self.client = self._make_client()
        # connect_async + loop_start: paho keeps retrying if the broker is down at startup.
        self.client.connect_async(cfg.mqtt_host, cfg.mqtt_port, keepalive=60)
        self.client.loop_start()

        last_stats = time.monotonic()
        try:
            while not self.stop_event.wait(HEARTBEAT_INTERVAL):
                if self.client.is_connected():
                    self._touch_heartbeat()
                now = time.monotonic()
                if now - last_stats >= cfg.stats_interval:
                    log.info("stats %s", format_stats(self.stats.take_window(), now - last_stats))
                    last_stats = now
        finally:
            self.client.disconnect()
            self.client.loop_stop()
            self.sender.close()
            log.info(
                "Stopped. Totals: %s",
                format_stats(self.stats.total, 0).split(": ", 1)[1],
            )
        if self.fatal_error:
            log.error(self.fatal_error)
            return 2
        return 0


# --------------------------------------------------------------------------- entry points


def healthcheck() -> int:
    """Exit 0 if the heartbeat file was touched recently (i.e. connected to the broker)."""
    path = os.environ.get("HEARTBEAT_FILE", "/tmp/mqtt-ode-bridge.heartbeat")
    try:
        age = time.time() - os.path.getmtime(path)
    except OSError:
        print("unhealthy: no heartbeat file")
        return 1
    if age > HEARTBEAT_MAX_AGE:
        print(f"unhealthy: heartbeat is {age:.0f}s old")
        return 1
    print(f"healthy: heartbeat {age:.0f}s old")
    return 0


def main() -> int:
    if "--healthcheck" in sys.argv[1:]:
        return healthcheck()
    logging.basicConfig(
        level=os.environ.get("LOG_LEVEL", "INFO").upper(),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    try:
        config = Config.from_env()
    except (OSError, ValueError) as exc:
        log.error("Invalid configuration: %s", exc)
        return 2
    problems = config.validate()
    if problems:
        for problem in problems:
            log.error("Invalid configuration: %s", problem)
        return 2

    bridge = Bridge(config)

    def stop(signum, _frame):
        log.info("Received %s, shutting down", signal.Signals(signum).name)
        bridge.stop_event.set()

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    return bridge.run()


if __name__ == "__main__":
    sys.exit(main())
