import os
import socket
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import mqtt_ode_bridge as b  # noqa: E402

MAP = bytes.fromhex("0012817c38063000220017489c")
SPAT = bytes.fromhex("001380c0219ab46ac5aae660")
BSM = bytes.fromhex("0014251bf5d3d50d10a1e66883")
PSM = bytes.fromhex("00201a000000020000")
TIM = bytes.fromhex("001f4a201692398244a4872f")
SIZES = {n: t.buffer_size for n, t in b.MESSAGE_TYPES.items()}


def make_config(**overrides):
    base = dict(
        mqtt_host="broker",
        mqtt_port=1883,
        mqtt_username="",
        mqtt_password="",
        mqtt_tls=False,
        mqtt_tls_insecure=False,
        mqtt_client_id="",
        mqtt_topics=["MAP/#"],
        mqtt_qos=0,
        ode_host="ode",
        ports={n: t.port for n, t in b.MESSAGE_TYPES.items()},
        buffer_sizes=dict(SIZES),
        use_generic_port=False,
        generic_port=b.GENERIC_PORT,
        routes=[],
        forward_retained=False,
        dry_run=False,
        stats_interval=60,
        heartbeat_file="/tmp/unused",
    )
    base.update(overrides)
    return b.Config(**base)


class FakeSender:
    def __init__(self):
        self.sent = []

    def send(self, data, host, port):
        self.sent.append((data, host, port))

    def close(self):
        pass


# --- normalize_payload -------------------------------------------------------


def test_binary_payload_passes_through():
    assert b.normalize_payload(MAP) == MAP


def test_ascii_hex_payload_is_converted():
    assert b.normalize_payload(BSM.hex().encode()) == BSM
    assert b.normalize_payload(BSM.hex().upper().encode()) == BSM


def test_ascii_hex_with_trailing_newline():
    assert b.normalize_payload(BSM.hex().encode() + b"\r\n") == BSM


def test_odd_length_or_non_hex_text_is_left_alone():
    assert b.normalize_payload(b"00142") == b"00142"
    assert b.normalize_payload(b"hello!") == b"hello!"


# --- start flags ---------------------------------------------------------------


def test_start_flag_at_offset_zero():
    assert b.find_start_flag(MAP, b"\x00\x12") == 0


def test_start_flag_after_header():
    headered = bytes.fromhex("03810040038081") + MAP
    assert b.find_start_flag(headered, b"\x00\x12") == 7


def test_start_flag_missing():
    assert b.find_start_flag(SPAT, b"\x00\x12") == -1


def test_flag_at_offset_one_is_not_accepted():
    # ODE skips the first 2 bytes unless the flag is at 0, so a hit at 1 doesn't count.
    assert b.find_start_flag(b"\x01\x00\x12\x55", b"\x00\x12") == -1


def test_detect_type_picks_earliest_flag():
    assert b.detect_type(BSM) == "BSM"
    assert b.detect_type(b"\xff\xff" + MAP) == "MAP"
    assert b.detect_type(b"\xff\xff\xff") is None


# --- classify -------------------------------------------------------------------


@pytest.mark.parametrize(
    "topic,payload,expected_type",
    [
        ("MAP/unsigned/J2735/DDOT/2062", MAP, "MAP"),
        ("SPAT/unsigned/J2735/DDOT/2062", SPAT, "SPAT"),
        ("BSM/unsigned/J2735/DDOT/M1st", BSM.hex().encode(), "BSM"),
        ("PSM/unsigned/J2735/DDOT/M3rd", PSM.hex().encode(), "PSM"),
        ("TIM/unsigned/J2735/d/q/c/j/r/6/2/v/", TIM, "TIM"),
    ],
)
def test_classify_send(topic, payload, expected_type):
    outcome, name, data = b.classify(topic, payload, SIZES)
    assert (outcome, name) == ("send", expected_type)
    assert data == b.normalize_payload(payload)


def test_classify_unknown_type():
    assert b.classify("RSM/x", MAP, SIZES)[0] == "unknown_type"


def test_classify_no_start_flag():
    assert b.classify("MAP/x", b"garbage!", SIZES)[0] == "no_start_flag"


def test_classify_type_mismatch():
    # A BSM frame that happens to contain 0x0012 later, published on a MAP topic.
    assert b.classify("MAP/x", BSM + b"\x00\x12", SIZES)[0] == "type_mismatch"


def test_classify_oversize_uses_per_type_buffer():
    big_spat = b"\x00\x13" + b"\x01" * 499  # 501 bytes > 500
    assert b.classify("SPAT/x", big_spat, SIZES)[0] == "oversize"
    big_map = b"\x00\x12" + b"\x01" * 1000  # fits MAP's 2048
    assert b.classify("MAP/x", big_map, SIZES)[0] == "send"


def test_lowercase_topic_type():
    assert b.classify("map/x", MAP, SIZES)[:2] == ("send", "MAP")


# --- routing --------------------------------------------------------------------


def test_route_per_type_port():
    router = b.Router(make_config())
    assert router.route("MAP/unsigned/J2735/DDOT/2062", "MAP") == ("ode", 44920)
    assert router.route("BSM/unsigned/J2735/DDOT/M1st", "BSM") == ("ode", 46800)


def test_route_generic_port():
    router = b.Router(make_config(use_generic_port=True))
    assert router.route("SPAT/x", "SPAT") == ("ode", 44990)


def test_route_override_wins_and_supports_wildcards():
    routes = b.parse_routes("MAP/+/J2735/DDOT/2062=15003, SPAT/#=other:16003", "ode")
    router = b.Router(make_config(routes=routes, use_generic_port=True))
    assert router.route("MAP/unsigned/J2735/DDOT/2062", "MAP") == ("ode", 15003)
    assert router.route("SPAT/unsigned/J2735/DDOT/9", "SPAT") == ("other", 16003)
    assert router.route("MAP/unsigned/J2735/DDOT/1", "MAP") == ("ode", 44990)


def test_parse_routes_rejects_bad_entry():
    with pytest.raises(ValueError):
        b.parse_routes("MAP/#", "ode")


# --- bridge.handle_message ------------------------------------------------------


def test_handle_message_sends_normalized_bytes():
    sender = FakeSender()
    bridge = b.Bridge(make_config(), sender=sender)
    bridge.handle_message("BSM/unsigned/J2735/DDOT/M1st", BSM.hex().encode(), retain=False)
    assert sender.sent == [(BSM, "ode", 46800)]
    assert bridge.stats.total[("BSM", "sent")] == 1


def test_handle_message_skips_retained_by_default():
    sender = FakeSender()
    bridge = b.Bridge(make_config(), sender=sender)
    bridge.handle_message("MAP/x", MAP, retain=True)
    assert sender.sent == []
    assert bridge.stats.total[("MAP", "skipped_retained")] == 1


def test_handle_message_forwards_retained_when_enabled():
    sender = FakeSender()
    bridge = b.Bridge(make_config(forward_retained=True), sender=sender)
    bridge.handle_message("MAP/x", MAP, retain=True)
    assert len(sender.sent) == 1


def test_dry_run_sends_nothing():
    sender = FakeSender()
    bridge = b.Bridge(make_config(dry_run=True), sender=sender)
    bridge.handle_message("MAP/x", MAP, retain=False)
    assert sender.sent == []
    assert bridge.stats.total[("MAP", "dry_run")] == 1


def test_drops_are_counted_not_sent():
    sender = FakeSender()
    bridge = b.Bridge(make_config(), sender=sender)
    bridge.handle_message("MAP/x", b"not uper", retain=False)
    assert sender.sent == []
    assert bridge.stats.total[("MAP", "no_start_flag")] == 1


def test_send_error_is_counted():
    class FailingSender(FakeSender):
        def send(self, data, host, port):
            raise OSError("network unreachable")

    bridge = b.Bridge(make_config(), sender=FailingSender())
    bridge.handle_message("MAP/x", MAP, retain=False)
    assert bridge.stats.total[("MAP", "send_error")] == 1


# --- real UDP -------------------------------------------------------------------


def test_udp_sender_delivers_exact_bytes():
    receiver = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    receiver.bind(("127.0.0.1", 0))
    receiver.settimeout(2)
    port = receiver.getsockname()[1]
    sender = b.UdpSender()
    try:
        sender.send(MAP, "localhost", port)
        data, _ = receiver.recvfrom(4096)
        assert data == MAP
    finally:
        sender.close()
        receiver.close()


# --- config ---------------------------------------------------------------------


def test_config_from_env(monkeypatch, tmp_path):
    secret = tmp_path / "pw"
    secret.write_text("s3cret\n")
    monkeypatch.setenv("MQTT_HOST", "broker")
    monkeypatch.setenv("MQTT_PASSWORD_FILE", str(secret))
    monkeypatch.setenv("ODE_HOST", "ode")
    monkeypatch.setenv("ODE_MAP_PORT", "1234")
    monkeypatch.setenv("ODE_ROUTES", "MAP/#=9999")
    monkeypatch.delenv("MQTT_TOPICS", raising=False)
    cfg = b.Config.from_env()
    assert cfg.mqtt_password == "s3cret"
    assert cfg.ports["MAP"] == 1234 and cfg.ports["BSM"] == 46800
    assert cfg.routes == [("MAP/#", "ode", 9999)]
    assert len(cfg.mqtt_topics) == 5
    assert cfg.validate() == []
    assert "s3cret" not in repr(cfg)


def test_generic_port_uses_generic_buffer(monkeypatch):
    monkeypatch.setenv("ODE_USE_GENERIC_PORT", "true")
    cfg = b.Config.from_env()
    assert set(cfg.buffer_sizes.values()) == {b.GENERIC_BUFFER_SIZE}


def test_validate_requires_hosts(monkeypatch):
    for name in ("MQTT_HOST", "ODE_HOST", "DRY_RUN"):
        monkeypatch.delenv(name, raising=False)
    problems = b.Config.from_env().validate()
    assert any("MQTT_HOST" in p for p in problems)
    assert any("ODE_HOST" in p for p in problems)
