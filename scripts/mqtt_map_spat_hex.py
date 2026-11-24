#!/usr/bin/env python3
"""Subscribe to MAP, SPaT and BSM topics, track unique MAPs, and link SPaTs and nearby BSMs to them.

The broker publishes UPER-encoded J2735 MessageFrames (raw binary) on topics such as:

    MAP/unsigned/J2735/DDOT/2062
    SPAT/unsigned/J2735/DDOT/2062
    BSM/unsigned/J2735/...

MAP and SPaT payloads are raw binary, which is why they look like garbage in MQTT
Explorer; BSM (and PSM) payloads on the same broker are ASCII hex text. Both forms are
normalized to bytes. For every message the script prints the payload as hex. In
addition it:

* identifies unique MAP payloads (by SHA-256 of the bytes) and labels each one
  M1, M2, ...;
* decodes each MAP and SPaT with the J2735 serializer container (the same approach as
  v2x-mec/scripts/utils.py: POST the UPER hex to /uper/hex/jer) to read the
  intersection reference IDs;
* associates each SPaT with the unique MAP whose intersection ID (intersections[].id.id)
  matches. The region field is ignored, so a MAP and SPaT whose regions differ (or
  where only one sets a region) are still linked. If several unique MAPs describe
  the same intersection, the most recently received one wins;
* associates each BSM with every current unique MAP that has an intersection
  reference point (refPoint) within --bsm-radius metres (default 200) of the BSM's
  coreData position. BSMs need the decoder (their position is inside the payload).
  BSMs can arrive at hundreds per second, so in text mode only BSMs linked to a MAP
  are printed (--all-bsm prints every one); a status line every 10 s reports how
  many arrived and how close the nearest one came to a MAP.

If the serializer is not reachable (or --no-decode is given), SPaTs are associated
with MAPs by topic instead: a SPaT on SPAT/<rest> matches the MAP on MAP/<rest>.

CV Manager export (--cvmanager-dir DIR):
    The CV Manager "ASN.1 Decoding" dialog (Intersection Map > Decoder Mode >
    Decode + Render Data) uploads a file per table (MAP, SPaT, BSM), splits it on
    "\\n", and sends each non-empty line to POST /asn1/decoder/raw as asn1Message.
    The backend strips anything before the 0012/0013 start flag and then hex-parses
    the rest of the line, so each line must be bare UPER hex -- JSON lines would
    fail to decode. For each unique MAP this writes, under DIR/run-<UTC time>/:

        M1_2062_MAP.txt    the MAP hex (one line)        -> upload to the MAP table
        M1_2062_SPAT.txt   its SPaT hex, one per line     -> upload to the SPaT table
        M1_2062_BSM.txt    nearby BSM hex, one per line   -> upload to the BSM table

    Then select the MAP in the dialog; CV Manager shows SPaTs whose intersection ID
    matches it, and draws every selected BSM regardless of intersection or time
    slider -- which is why only BSMs near the MAP go into its file. CV Manager
    renders only the first intersection of a MAP. The browser decodes every
    uploaded line at once, so SPaT and BSM files are capped at --cvmanager-max-spat
    and --cvmanager-max-bsm lines per MAP (default 1000 each).

Google Earth KML (--kml FILE):
    Writes a KML with a "MAPs" folder (each unique MAP's intersection reference
    points and lanes -- node-LatLon and node-XY offsets are both supported; ingress
    lanes cyan, egress magenta) and a "BSMs" folder per BSM topic with each vehicle's
    track (yellow line, one point per vehicle per --kml-bsm-interval seconds) and its
    latest position. The file is rewritten every --kml-interval seconds and on exit,
    so it can be opened in Google Earth while the script runs. Needs the decoder.

Setup (from the repo root):
    python3 -m venv scripts/.venv
    scripts/.venv/bin/pip install -r scripts/requirements-mqtt.txt
    source scripts/.venv/bin/activate

    # J2735 serializer used for decoding (same image as v2x-mec/scripts/docker-compose.yml)
    docker run -d --name j2735-2024-api -p 4000:4000 iyourshaw/j2735-2024-api:v1.0.1

Examples:
    export MQTT_USERNAME=myuser MQTT_PASSWORD=mypass
    python3 scripts/mqtt_map_spat_hex.py --host v2x.aitoms.net

    # Only print each new unique MAP; write the MAP/SPaT summary on exit (Ctrl+C)
    python3 scripts/mqtt_map_spat_hex.py --host v2x.aitoms.net --maps-only \\
        --summary-file map_spat_summary.json

    # Files for upload to the CV Manager ASN.1 decoder (see above)
    python3 scripts/mqtt_map_spat_hex.py --host v2x.aitoms.net --maps-only \\
        --cvmanager-dir cvmanager_upload

    # Google Earth view of every MAP and BSM on the broker (open the .kml in Google Earth)
    python3 scripts/mqtt_map_spat_hex.py --host v2x.aitoms.net --maps-only --kml broker.kml

    # JSON lines including the decoded JER, appended to a file
    python3 scripts/mqtt_map_spat_hex.py --host v2x.aitoms.net --jsonl --decoded \\
        --output messages.jsonl
"""

import argparse
import getpass
import hashlib
import json
import math
import os
import ssl
import sys
import time
import urllib.error
import urllib.request
from collections import Counter, deque
from datetime import datetime, timezone
from types import SimpleNamespace
from xml.sax.saxutils import escape

import paho.mqtt.client as mqtt

DEFAULT_TOPICS = [
    "MAP/unsigned/J2735/#",
    "SPAT/unsigned/J2735/#",
    "BSM/unsigned/J2735/#",
]
DEFAULT_DECODER_URL = "http://localhost:4000"
DECODER_DOCKER_HINT = (
    "docker run -d --name j2735-2024-api -p 4000:4000 iyourshaw/j2735-2024-api:v1.0.1"
)


def now_iso():
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


def parse_args():
    parser = argparse.ArgumentParser(
        description="Print MAP/SPaT/BSM hex payloads, identify unique MAPs, and associate "
        "SPaTs and nearby BSMs with them."
    )
    parser.add_argument("--host", default=os.environ.get("MQTT_HOST", "v2x.aitoms.net"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("MQTT_PORT", "1883")))
    parser.add_argument(
        "--username",
        default=os.environ.get("MQTT_USERNAME"),
        help="Defaults to $MQTT_USERNAME; prompted if unset.",
    )
    parser.add_argument(
        "--password",
        default=os.environ.get("MQTT_PASSWORD"),
        help="Defaults to $MQTT_PASSWORD; prompted if unset. Prefer the env var "
        "over this flag so the password doesn't land in shell history.",
    )
    parser.add_argument(
        "--topic",
        action="append",
        dest="topics",
        help="Topic filter to subscribe to (repeatable). "
        f"Default: {', '.join(DEFAULT_TOPICS)}",
    )
    parser.add_argument("--qos", type=int, choices=(0, 1, 2), default=0)
    parser.add_argument("--client-id", default="", help="Default: broker-assigned.")
    parser.add_argument("--tls", action="store_true", help="Connect with TLS.")
    parser.add_argument(
        "--insecure",
        action="store_true",
        help="With --tls, skip certificate verification (self-signed brokers).",
    )
    parser.add_argument(
        "--decoder-url",
        default=os.environ.get("J2735_API_URL", DEFAULT_DECODER_URL),
        help=f"J2735 serializer base URL (default: $J2735_API_URL or {DEFAULT_DECODER_URL}).",
    )
    parser.add_argument(
        "--no-decode",
        action="store_true",
        help="Don't decode; associate SPaT with MAP by topic only.",
    )
    parser.add_argument("--jsonl", action="store_true", help="Emit one JSON object per message.")
    parser.add_argument(
        "--decoded", action="store_true", help="With --jsonl, include the decoded JER message."
    )
    parser.add_argument(
        "--maps-only",
        action="store_true",
        help="Only print new unique MAPs (SPaTs/BSMs are still associated and counted).",
    )
    parser.add_argument(
        "--all-bsm",
        action="store_true",
        help="In text mode, also print BSMs not linked to any MAP (can be hundreds per second).",
    )
    parser.add_argument(
        "--bsm-radius",
        type=float,
        default=200.0,
        help="Associate a BSM with a MAP when it is within this many metres of one of the "
        "MAP's intersection reference points (default 200).",
    )
    parser.add_argument("--output", help="Append per-message output to this file as well as stdout.")
    parser.add_argument(
        "--summary-file", help="Write the MAP/SPaT association summary as JSON on exit."
    )
    parser.add_argument(
        "--cvmanager-dir",
        help="Write per-MAP MAP/SPaT/BSM hex files (one message per line) for upload to the "
        "CV Manager ASN.1 decoder into a run-<time> folder under this directory.",
    )
    parser.add_argument(
        "--cvmanager-max-spat",
        type=int,
        default=1000,
        help="Max SPaT lines per MAP in the CV Manager files (default 1000; 0 = no limit).",
    )
    parser.add_argument(
        "--cvmanager-max-bsm",
        type=int,
        default=1000,
        help="Max BSM lines per MAP in the CV Manager files (default 1000; 0 = no limit).",
    )
    parser.add_argument(
        "--kml",
        help="Write a Google Earth KML of MAP intersections/lanes and BSM vehicle tracks.",
    )
    parser.add_argument(
        "--kml-interval",
        type=float,
        default=30.0,
        help="Rewrite the KML every this many seconds while running (default 30).",
    )
    parser.add_argument(
        "--kml-bsm-interval",
        type=float,
        default=1.0,
        help="Keep at most one BSM track point per vehicle per this many seconds (default 1).",
    )
    parser.add_argument(
        "--keep-spat",
        type=int,
        default=5,
        help="Most recent SPaT hex payloads to keep per MAP in the summary (default 5).",
    )
    parser.add_argument(
        "--count", type=int, default=0, help="Exit after this many messages (0 = run forever)."
    )
    args = parser.parse_args()

    if not args.topics:
        args.topics = DEFAULT_TOPICS
    if not args.username:
        args.username = input("MQTT username: ")
    if args.password is None:
        args.password = getpass.getpass("MQTT password: ")
    return args


class J2735Decoder:
    """Client for the iyourshaw/j2735-2024-api container (UPER hex -> JER JSON)."""

    def __init__(self, base_url, timeout=5.0):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def healthy(self):
        try:
            with urllib.request.urlopen(f"{self.base_url}/health", timeout=self.timeout) as resp:
                return resp.status == 200
        except (urllib.error.URLError, OSError):
            return False

    def decode(self, hex_payload):
        """Return the decoded MessageFrame as a dict, or raise ValueError."""
        req = urllib.request.Request(
            f"{self.base_url}/uper/hex/jer",
            data=hex_payload.encode("ascii"),
            headers={"Content-Type": "text/plain"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                return json.loads(resp.read())
        except urllib.error.HTTPError as exc:
            raise ValueError(f"decoder returned HTTP {exc.code}") from exc
        except (urllib.error.URLError, OSError, json.JSONDecodeError) as exc:
            raise ValueError(f"decoder error: {exc}") from exc


def intersection_key(intersection):
    """The id.id of an IntersectionGeometry / IntersectionState. id.region is ignored."""
    return intersection.get("id", {}).get("id")


def format_key(key):
    return str(key)


# J2735 Latitude/Longitude are in 1/10 micro-degrees; these values mean "unavailable".
LAT_UNAVAILABLE = 900000001
LONG_UNAVAILABLE = 1800000001
EARTH_RADIUS_M = 6371008.8


def j2735_position(obj):
    """(lat, lon) in degrees from a J2735 position, or None.

    Position3D / BSM coreData use "long"; Node-LLmD-64b (node-LatLon) uses "lon".
    """
    if not obj:
        return None
    lat = obj.get("lat")
    lon = obj.get("long", obj.get("lon"))
    if lat is None or lon is None or lat == LAT_UNAVAILABLE or lon == LONG_UNAVAILABLE:
        return None
    return lat / 1e7, lon / 1e7


def distance_m(a, b):
    """Great-circle (haversine) distance in metres between two (lat, lon) points."""
    lat1, lon1, lat2, lon2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = (
        math.sin((lat2 - lat1) / 2) ** 2
        + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    )
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(h))


HEX_DIGITS = b"0123456789abcdefABCDEF"


def normalize_payload(payload):
    """Return (UPER bytes, "binary" | "ascii-hex").

    Some publishers send the UPER encoding as ASCII hex text (e.g. b"001425...")
    instead of raw bytes. A raw J2735 MessageFrame starts with a 0x00 byte, so it can
    never be mistaken for hex text.
    """
    text = payload.strip()
    if text and len(text) % 2 == 0 and not text.translate(None, HEX_DIGITS):
        return bytes.fromhex(text.decode("ascii")), "ascii-hex"
    return payload, "binary"


def offset_position(origin, x_m, y_m):
    """(lat, lon) of a point x_m east and y_m north of origin (flat-earth, fine for a MAP)."""
    lat0, lon0 = origin
    lat = lat0 + math.degrees(y_m / EARTH_RADIUS_M)
    lon = lon0 + math.degrees(x_m / (EARTH_RADIUS_M * math.cos(math.radians(lat0))))
    return lat, lon


def lane_geometries(intersection):
    """Lanes of a decoded IntersectionGeometry as dicts with a list of (lat, lon) points.

    node-LatLon nodes are absolute; node-XY1..6 nodes are cm offsets from the previous
    node (the first from refPoint). Computed lanes (offsets from another lane) are
    skipped.
    """
    ref = j2735_position(intersection.get("refPoint"))
    lanes = []
    for lane in intersection.get("laneSet", []):
        nodes = lane.get("nodeList", {}).get("nodes")
        if not nodes or ref is None:
            continue
        points, current = [], ref
        for node in nodes:
            delta = node.get("delta", {})
            if "node-LatLon" in delta:
                current = j2735_position(delta["node-LatLon"]) or current
            else:
                xy = next((v for k, v in delta.items() if k.startswith("node-XY")), None)
                if xy is None:
                    continue
                current = offset_position(current, xy["x"] / 100.0, xy["y"] / 100.0)
            points.append(current)
        # LaneDirection BIT STRING: bit 0 ingressPath, bit 1 egressPath.
        direction = int(lane.get("laneAttributes", {}).get("directionalUse", "00") or "00", 16)
        kind = "ingress" if direction & 0x80 else "egress" if direction & 0x40 else "other"
        lanes.append(
            {"laneID": lane.get("laneID"), "name": lane.get("name"), "kind": kind, "points": points}
        )
    return lanes


class KmlWriter:
    """Collects MAP geometry and BSM tracks and writes them as a Google Earth KML file."""

    STYLES = {
        # KML colours are aabbggrr.
        "ingress": '<LineStyle><color>ffffff00</color><width>4</width></LineStyle>',
        "egress": '<LineStyle><color>ffff00ff</color><width>4</width></LineStyle>',
        "other": '<LineStyle><color>ffaaaaaa</color><width>3</width></LineStyle>',
        "refpoint": (
            "<IconStyle><scale>1.2</scale><Icon><href>"
            "http://maps.google.com/mapfiles/kml/shapes/target.png</href></Icon></IconStyle>"
        ),
        "track": '<LineStyle><color>ff00ffff</color><width>2</width></LineStyle>',
        "vehicle": (
            "<IconStyle><scale>0.7</scale><Icon><href>"
            "http://maps.google.com/mapfiles/kml/shapes/cabs.png</href></Icon></IconStyle>"
            "<LabelStyle><scale>0.6</scale></LabelStyle>"
        ),
    }

    def __init__(self, path, bsm_interval):
        self.path = path
        self.bsm_interval = bsm_interval
        self.maps = []  # (record, [(intersection info, lanes)])
        self.vehicles = {}  # (topic, BSM id) -> vehicle dict

    def add_map(self, record, decoded):
        map_data = (decoded or {}).get("value", {}).get("MapData", {})
        self.maps.append(
            (record, [(ix, lane_geometries(ix)) for ix in map_data.get("intersections", [])])
        )

    def add_bsm(self, topic, core, position, received_at):
        key = (topic, core.get("id"))
        now = time.monotonic()
        vehicle = self.vehicles.setdefault(
            key, {"points": [], "count": 0, "first_seen": received_at, "last_t": None}
        )
        vehicle["count"] += 1
        vehicle["last_seen"] = received_at
        vehicle["last"] = position
        vehicle["core"] = core
        if vehicle["last_t"] is None or now - vehicle["last_t"] >= self.bsm_interval:
            vehicle["points"].append(position)
            vehicle["last_t"] = now

    @staticmethod
    def _coords(points):
        return " ".join(f"{lon:.7f},{lat:.7f},0" for lat, lon in points)

    @staticmethod
    def _placemark(name, style, geometry, description=""):
        desc = f"<description>{escape(description)}</description>" if description else ""
        return (
            f"<Placemark><name>{escape(name)}</name>{desc}"
            f"<styleUrl>#{style}</styleUrl>{geometry}</Placemark>"
        )

    def _map_folders(self, tracker):
        out = []
        for record, intersections in self.maps:
            state = "current" if tracker.is_current(record) else "superseded"
            parts = []
            for ix, lanes in intersections:
                ix_id = intersection_key(ix)
                ref = j2735_position(ix.get("refPoint"))
                if ref:
                    desc = (
                        f"Intersection {ix_id} (region {ix.get('id', {}).get('region')}), "
                        f"revision {ix.get('revision')}\nMAP {record['label']} [{state}], "
                        f"{record['length']} bytes, sha256 {record['sha256'][:12]}\n"
                        f"Topics: {', '.join(record['topics'])}\n"
                        f"SPaTs linked: {record['spat_count']}, nearby BSMs: {record['bsm_count']}"
                    )
                    parts.append(
                        self._placemark(
                            f"{ix_id}", "refpoint",
                            f"<Point><coordinates>{self._coords([ref])}</coordinates></Point>", desc,
                        )
                    )
                for lane in lanes:
                    if len(lane["points"]) < 2:
                        continue
                    name = f"Lane {lane['laneID']}" + (f" {lane['name']}" if lane["name"] else "")
                    parts.append(
                        self._placemark(
                            f"{name} ({lane['kind']})", lane["kind"],
                            "<LineString><tessellate>1</tessellate>"
                            f"<coordinates>{self._coords(lane['points'])}</coordinates></LineString>",
                        )
                    )
            ids = ", ".join(str(intersection_key(ix)) for ix, _ in intersections) or "not decoded"
            out.append(
                f"<Folder><name>{escape(record['label'])} - {escape(ids)} [{state}]</name>"
                f"{''.join(parts)}</Folder>"
            )
        return "".join(out)

    def _bsm_folders(self):
        by_topic = {}
        for (topic, bsm_id), v in self.vehicles.items():
            by_topic.setdefault(topic, []).append((bsm_id, v))
        out = []
        for topic in sorted(by_topic):
            parts = []
            for bsm_id, v in sorted(by_topic[topic], key=lambda item: item[1]["first_seen"]):
                core = v["core"]
                speed = core.get("speed")
                heading = core.get("heading")
                desc = (
                    f"BSM id {bsm_id} on {topic}\n{v['count']} BSMs, "
                    f"{v['first_seen']} to {v['last_seen']}\n"
                    f"Last speed: {f'{speed * 0.02:.1f} m/s' if speed not in (None, 8191) else 'n/a'}, "
                    f"heading: {f'{heading * 0.0125:.1f} deg' if heading not in (None, 28800) else 'n/a'}"
                )
                vehicle_parts = [
                    self._placemark(
                        f"{bsm_id}", "vehicle",
                        f"<Point><coordinates>{self._coords([v['last']])}</coordinates></Point>", desc,
                    )
                ]
                if len(v["points"]) >= 2:
                    vehicle_parts.append(
                        self._placemark(
                            f"{bsm_id} track", "track",
                            "<LineString><tessellate>1</tessellate>"
                            f"<coordinates>{self._coords(v['points'])}</coordinates></LineString>",
                        )
                    )
                parts.append(
                    f"<Folder><name>{escape(str(bsm_id))}</name>{''.join(vehicle_parts)}</Folder>"
                )
            out.append(
                f"<Folder><name>{escape(topic)} ({len(parts)} vehicles)</name>{''.join(parts)}</Folder>"
            )
        return "".join(out)

    def _look_at(self):
        points = [
            j2735_position(ix.get("refPoint")) for _, ixs in self.maps for ix, _ in ixs
        ] + [v["last"] for v in self.vehicles.values()]
        points = [p for p in points if p]
        if not points:
            return ""
        lats, lons = [p[0] for p in points], [p[1] for p in points]
        span = max(distance_m((min(lats), min(lons)), (max(lats), max(lons))), 300)
        return (
            f"<LookAt><longitude>{(min(lons) + max(lons)) / 2:.7f}</longitude>"
            f"<latitude>{(min(lats) + max(lats)) / 2:.7f}</latitude>"
            f"<range>{span * 1.5:.0f}</range><tilt>0</tilt><heading>0</heading></LookAt>"
        )

    def write(self, tracker):
        styles = "".join(f'<Style id="{k}">{v}</Style>' for k, v in self.STYLES.items())
        doc = (
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<kml xmlns="http://www.opengis.net/kml/2.2"><Document>'
            f"<name>MQTT MAP / BSM ({escape(now_iso())})</name>{self._look_at()}{styles}"
            f"<Folder><name>MAPs ({len(self.maps)})</name>{self._map_folders(tracker)}</Folder>"
            f"<Folder><name>BSMs ({len(self.vehicles)} vehicles)</name>{self._bsm_folders()}</Folder>"
            "</Document></kml>\n"
        )
        tmp = f"{self.path}.tmp"
        with open(tmp, "w", encoding="utf-8") as fh:
            fh.write(doc)
        os.replace(tmp, self.path)  # atomic, so Google Earth never reads a half-written file


def topic_key(topic):
    """MAP/unsigned/J2735/DDOT/2062 -> unsigned/J2735/DDOT/2062 (shared by MAP and SPAT)."""
    return topic.split("/", 1)[1] if "/" in topic else topic


class MapSpatTracker:
    def __init__(self, keep_spat):
        self.keep_spat = keep_spat
        self.maps = {}  # sha256 -> MAP record
        self.by_intersection = {}  # intersection id.id -> sha256 of the latest MAP for it
        self.by_topic = {}  # topic key -> sha256 of the latest MAP on it
        self.unmatched_spat = Counter()  # intersection (or topic) -> SPaTs with no MAP
        self.spat_total = 0
        self.bsm_total = 0
        self.bsm_not_near = 0  # had a position but no current MAP within the radius
        self.bsm_no_position = 0  # not decoded, or lat/long unavailable
        self.bsm_nearest = None  # (distance m, MAP label) closest any BSM has come to a MAP
        self.decode_errors = Counter()

    def add_map(self, topic, payload, decoded, received_at):
        """Record a MAP payload. Returns (record, is_new)."""
        digest = hashlib.sha256(payload).hexdigest()
        record = self.maps.get(digest)
        is_new = record is None
        if is_new:
            intersections = []
            msg_issue_revision = None
            if decoded is not None:
                map_data = decoded.get("value", {}).get("MapData", {})
                msg_issue_revision = map_data.get("msgIssueRevision")
                for ix in map_data.get("intersections", []):
                    ix_id = intersection_key(ix)
                    intersections.append(
                        {
                            "key": format_key(ix_id),
                            # Informational only; not used for SPaT association.
                            "region": ix.get("id", {}).get("region"),
                            "id": ix_id,
                            "name": ix.get("name"),
                            "revision": ix.get("revision"),
                            "refPoint": ix.get("refPoint"),
                        }
                    )
            record = {
                "label": f"M{len(self.maps) + 1}",
                "sha256": digest,
                "length": len(payload),
                "hex": payload.hex().upper(),
                "msgIssueRevision": msg_issue_revision,
                "intersections": intersections,
                "topics": [],
                "first_seen": received_at,
                "last_seen": received_at,
                "map_count": 0,
                "spat_count": 0,
                "spat_first_seen": None,
                "spat_last_seen": None,
                "recent_spat": deque(maxlen=self.keep_spat),
                "bsm_count": 0,
                "bsm_first_seen": None,
                "bsm_last_seen": None,
                # Earlier unique MAPs this one replaced for the same intersection(s).
                "supersedes": [],
            }
            self.maps[digest] = record

        record["map_count"] += 1
        record["last_seen"] = received_at
        if topic not in record["topics"]:
            record["topics"].append(topic)

        # The latest MAP for an intersection / topic is the one SPaTs get associated with.
        for ix in record["intersections"]:
            key = ix["id"]
            previous = self.by_intersection.get(key)
            if previous and previous != digest:
                prev_label = self.maps[previous]["label"]
                if prev_label not in record["supersedes"]:
                    record["supersedes"].append(prev_label)
            self.by_intersection[key] = digest
        self.by_topic[topic_key(topic)] = digest
        return record, is_new

    def add_spat(self, topic, payload, decoded, received_at):
        """Associate a SPaT with MAP(s). Returns (matched records, intersection keys, method)."""
        self.spat_total += 1
        matched = []
        keys = []
        if decoded is not None:
            method = "intersection-id"
            spat = decoded.get("value", {}).get("SPAT", {})
            for ix in spat.get("intersections", []):
                key = intersection_key(ix)
                keys.append(key)
                digest = self.by_intersection.get(key)
                if digest is None:
                    self.unmatched_spat[format_key(key)] += 1
                elif self.maps[digest] not in matched:
                    matched.append(self.maps[digest])
        else:
            method = "topic"
            digest = self.by_topic.get(topic_key(topic))
            if digest is None:
                self.unmatched_spat[f"topic:{topic_key(topic)}"] += 1
            else:
                matched.append(self.maps[digest])

        hex_payload = payload.hex().upper()
        for record in matched:
            record["spat_count"] += 1
            record["spat_first_seen"] = record["spat_first_seen"] or received_at
            record["spat_last_seen"] = received_at
            record["recent_spat"].append(
                {"received_at": received_at, "topic": topic, "hex": hex_payload}
            )
        return matched, keys, method

    def add_bsm(self, payload, decoded, received_at, radius_m):
        """Associate a BSM with every current MAP within radius_m.

        Returns (list of (record, distance in m) nearest first, (lat, lon) or None).
        """
        self.bsm_total += 1
        position = None
        if decoded is not None:
            bsm = decoded.get("value", {}).get("BasicSafetyMessage", {})
            position = j2735_position(bsm.get("coreData"))
        if position is None:
            self.bsm_no_position += 1
            return [], None

        matched = []
        for record in self.maps.values():
            if not self.is_current(record):
                continue
            points = [j2735_position(ix["refPoint"]) for ix in record["intersections"]]
            dists = [distance_m(position, p) for p in points if p is not None]
            if not dists:
                continue
            nearest = min(dists)
            if self.bsm_nearest is None or nearest < self.bsm_nearest[0]:
                self.bsm_nearest = (nearest, record["label"])
            if nearest <= radius_m:
                matched.append((record, nearest))
        matched.sort(key=lambda pair: pair[1])

        if not matched:
            self.bsm_not_near += 1
        for record, _ in matched:
            record["bsm_count"] += 1
            record["bsm_first_seen"] = record["bsm_first_seen"] or received_at
            record["bsm_last_seen"] = received_at
        return matched, position

    def is_current(self, record):
        if record["intersections"]:
            return all(
                self.by_intersection.get(ix["id"]) == record["sha256"]
                for ix in record["intersections"]
            )
        return any(self.by_topic.get(topic_key(t)) == record["sha256"] for t in record["topics"])

    def summary(self):
        maps = []
        for record in self.maps.values():
            out = dict(record)
            out["recent_spat"] = list(record["recent_spat"])
            out["current"] = self.is_current(record)
            maps.append(out)
        return {
            "generated_at": now_iso(),
            "unique_map_count": len(self.maps),
            "spat_total": self.spat_total,
            "bsm_total": self.bsm_total,
            "bsm_not_near_any_map": self.bsm_not_near,
            "bsm_without_position": self.bsm_no_position,
            "bsm_nearest_m": round(self.bsm_nearest[0], 1) if self.bsm_nearest else None,
            "bsm_nearest_map": self.bsm_nearest[1] if self.bsm_nearest else None,
            "maps": maps,
            "unmatched_spat": dict(self.unmatched_spat),
            "decode_errors": dict(self.decode_errors),
        }


class CvManagerExporter:
    """Writes newline-delimited hex files the CV Manager ASN.1 decoder dialog can upload.

    Each line is sent verbatim to HexFormat.parseHex() after the start flag, so lines
    hold only hex and end with a bare LF (a CR would break parsing).
    """

    KINDS = ("SPAT", "BSM")  # per-MAP files that grow as messages arrive

    def __init__(self, base_dir, max_lines):
        """max_lines: {"SPAT": n, "BSM": n}; 0 means no limit."""
        self.dir = os.path.join(
            base_dir, datetime.now(timezone.utc).strftime("run-%Y%m%dT%H%M%SZ")
        )
        os.makedirs(self.dir, exist_ok=True)
        self.max_lines = max_lines
        self.files = {}  # (MAP label, kind) -> open file handle

    def _open(self, path, mode):
        return open(path, mode, encoding="ascii", newline="\n")

    def add_map(self, record):
        ids = "-".join(str(ix["id"]) for ix in record["intersections"])
        stem = f"{record['label']}_{ids}" if ids else record["label"]
        paths = {kind: os.path.join(self.dir, f"{stem}_{kind}.txt") for kind in ("MAP",) + self.KINDS}
        with self._open(paths["MAP"], "w") as fh:
            fh.write(record["hex"] + "\n")
        for kind in self.KINDS:
            self.files[(record["label"], kind)] = self._open(paths[kind], "w")
        record["cvmanager_files"] = paths
        record["cvmanager_lines"] = {kind: 0 for kind in self.KINDS}

    def add_line(self, record, kind, hex_payload):
        key = (record["label"], kind)
        fh = self.files.get(key)
        if fh is None:
            return
        limit = self.max_lines.get(kind, 0)
        if limit and record["cvmanager_lines"][kind] >= limit:
            fh.close()
            del self.files[key]
            print(
                f"CV Manager {kind} file for {record['label']} reached {limit} lines; "
                f"no more {kind}s will be written to it.",
                file=sys.stderr,
            )
            return
        fh.write(hex_payload + "\n")
        fh.flush()
        record["cvmanager_lines"][kind] += 1

    def close(self):
        for fh in self.files.values():
            fh.close()
        self.files.clear()


def describe_intersections(record):
    return ", ".join(
        f"{i['key']} rev {i['revision']}" + (f" ({i['name']})" if i.get("name") else "")
        for i in record["intersections"]
    ) or "intersections unknown (not decoded)"


def print_summary(summary, stream=sys.stderr):
    print("\n=== MAP / SPaT / BSM summary ===", file=stream)
    print(
        f"{summary['unique_map_count']} unique MAP payload(s), "
        f"{summary['spat_total']} SPaT message(s), {summary['bsm_total']} BSM message(s)",
        file=stream,
    )
    for m in summary["maps"]:
        state = "current" if m["current"] else "superseded"
        print(
            f"  {m['label']} [{state}] {describe_intersections(m)}; {m['length']} bytes; "
            f"sha256 {m['sha256'][:12]}; seen {m['map_count']}x; "
            f"{m['spat_count']} SPaT(s), {m['bsm_count']} nearby BSM(s)",
            file=stream,
        )
        if m["supersedes"]:
            print(f"      supersedes {', '.join(m['supersedes'])}", file=stream)
        print(f"      topics: {', '.join(m['topics'])}", file=stream)
        if m.get("cvmanager_files"):
            files, lines = m["cvmanager_files"], m["cvmanager_lines"]
            print(f"      CV Manager: {files['MAP']}", file=stream)
            print(f"                  {files['SPAT']} ({lines['SPAT']} lines)", file=stream)
            print(f"                  {files['BSM']} ({lines['BSM']} lines)", file=stream)
    if summary["unmatched_spat"]:
        print("  SPaTs with no matching MAP:", file=stream)
        for key, count in summary["unmatched_spat"].items():
            print(f"      {key}: {count}", file=stream)
    if summary["bsm_total"]:
        print(
            f"  BSMs not near any MAP: {summary['bsm_not_near_any_map']}; "
            f"without a position (not decoded / unavailable): {summary['bsm_without_position']}",
            file=stream,
        )
        if summary["bsm_nearest_m"] is not None:
            print(
                f"  Closest any BSM came to a MAP: {summary['bsm_nearest_m']:.0f} m "
                f"({summary['bsm_nearest_map']})",
                file=stream,
            )
    if summary["decode_errors"]:
        print(f"  Decode errors: {summary['decode_errors']}", file=stream)


def main():
    args = parse_args()

    decoder = None
    if not args.no_decode:
        candidate = J2735Decoder(args.decoder_url)
        if candidate.healthy():
            decoder = candidate
            print(f"Using J2735 decoder at {args.decoder_url}", file=sys.stderr)
        else:
            print(
                f"J2735 decoder not reachable at {args.decoder_url}; associating SPaT with MAP "
                "by topic instead, and BSMs can't be located so none will be linked to a MAP."
                f"\nStart it with: {DECODER_DOCKER_HINT}",
                file=sys.stderr,
            )

    tracker = MapSpatTracker(args.keep_spat)
    exporter = None
    if args.cvmanager_dir:
        exporter = CvManagerExporter(
            args.cvmanager_dir, {"SPAT": args.cvmanager_max_spat, "BSM": args.cvmanager_max_bsm}
        )
        print(f"Writing CV Manager upload files to {exporter.dir}", file=sys.stderr)
    kml = KmlWriter(args.kml, args.kml_bsm_interval) if args.kml else None
    if kml and decoder is None:
        print("Note: --kml needs the decoder for lane geometry and BSM positions.", file=sys.stderr)
    last_kml_write = time.monotonic()
    out_file = open(args.output, "a", encoding="utf-8") if args.output else None
    received = 0
    connect_failed = False

    def emit(line):
        print(line, flush=True)
        if out_file:
            out_file.write(line + "\n")
            out_file.flush()

    def decode(msg_type, hex_payload):
        """Returns (decoded dict or None, error string or None)."""
        if decoder is None:
            return None, None
        try:
            return decoder.decode(hex_payload), None
        except ValueError as exc:
            tracker.decode_errors[msg_type] += 1
            return None, str(exc)

    def handle_map(msg, hex_payload, received_at):
        # Only decode payloads we haven't seen; repeats of a known MAP reuse its record.
        digest = hashlib.sha256(msg.payload).hexdigest()
        if digest in tracker.maps:
            decoded, error = None, None
        else:
            decoded, error = decode("MAP", hex_payload)
        record, is_new = tracker.add_map(msg.topic, msg.payload, decoded, received_at)
        if is_new and kml:
            kml.add_map(record, decoded)
        if is_new and exporter:
            exporter.add_map(record)
            if len(record["intersections"]) > 1:
                print(
                    f"Note: {record['label']} has {len(record['intersections'])} intersections; "
                    "CV Manager renders only the first "
                    f"({record['intersections'][0]['key']}).",
                    file=sys.stderr,
                )

        if args.jsonl:
            if args.maps_only and not is_new:
                return
            row = {
                "received_at": received_at,
                "topic": msg.topic,
                "type": "MAP",
                "map": record["label"],
                "new_map": is_new,
                "sha256": record["sha256"],
                "intersections": [i["key"] for i in record["intersections"]],
                "length": len(msg.payload),
                "hex": hex_payload,
            }
            if error:
                row["decode_error"] = error
            if args.decoded and decoded is not None:
                row["decoded"] = decoded
            emit(json.dumps(row))
        elif is_new:
            extra = f"; supersedes {', '.join(record['supersedes'])}" if record["supersedes"] else ""
            err = f"; decode failed: {error}" if error else ""
            emit(
                f"{received_at} NEW MAP {record['label']} {msg.topic} ({len(msg.payload)} bytes; "
                f"{describe_intersections(record)}; sha256 {record['sha256'][:12]}{extra}{err})\n"
                f"{hex_payload}\n"
            )
        elif not args.maps_only:
            emit(f"{received_at} MAP {record['label']} (repeat) {msg.topic}")

    def handle_spat(msg, hex_payload, received_at):
        decoded, error = decode("SPAT", hex_payload)
        matched, keys, method = tracker.add_spat(msg.topic, msg.payload, decoded, received_at)
        # A payload our decoder rejected would only show up as an error in CV Manager.
        if exporter and error is None:
            for record in matched:
                exporter.add_line(record, "SPAT", hex_payload)
        if args.maps_only:
            return
        labels = [m["label"] for m in matched]
        if args.jsonl:
            row = {
                "received_at": received_at,
                "topic": msg.topic,
                "type": "SPAT",
                "maps": labels,
                "intersections": [format_key(k) for k in keys],
                "match_method": method,
                "length": len(msg.payload),
                "hex": hex_payload,
            }
            if error:
                row["decode_error"] = error
            if args.decoded and decoded is not None:
                row["decoded"] = decoded
            emit(json.dumps(row))
        else:
            target = ", ".join(labels) if labels else "NO MATCHING MAP"
            via = (
                f"intersection {', '.join(format_key(k) for k in keys)}" if keys else f"by {method}"
            )
            err = f"; decode failed: {error}" if error else ""
            emit(
                f"{received_at} SPAT -> {target} ({via}) {msg.topic} "
                f"({len(msg.payload)} bytes{err})\n{hex_payload}\n"
            )

    def handle_bsm(msg, hex_payload, received_at):
        nonlocal bsm_linked
        decoded, error = decode("BSM", hex_payload)
        matched, position = tracker.add_bsm(msg.payload, decoded, received_at, args.bsm_radius)
        if matched:
            bsm_linked += 1
        if kml and position:
            core = decoded["value"]["BasicSafetyMessage"]["coreData"]
            kml.add_bsm(msg.topic, core, position, received_at)
        if exporter and error is None:
            for record, _ in matched:
                exporter.add_line(record, "BSM", hex_payload)
        if args.maps_only:
            return
        if args.jsonl:
            row = {
                "received_at": received_at,
                "topic": msg.topic,
                "type": "BSM",
                "maps": [r["label"] for r, _ in matched],
                "distance_m": [round(d, 1) for _, d in matched],
                "lat": position[0] if position else None,
                "lon": position[1] if position else None,
                "length": len(msg.payload),
                "hex": hex_payload,
            }
            if error:
                row["decode_error"] = error
            if args.decoded and decoded is not None:
                row["decoded"] = decoded
            emit(json.dumps(row))
        elif matched or args.all_bsm:
            if matched:
                target = ", ".join(f"{r['label']} ({d:.0f} m)" for r, d in matched)
            elif position:
                target = f"no MAP within {args.bsm_radius:g} m"
            else:
                target = "position unknown"
            err = f"; decode failed: {error}" if error else ""
            emit(
                f"{received_at} BSM -> {target} {msg.topic} ({len(msg.payload)} bytes{err})\n"
                f"{hex_payload}\n"
            )

    def on_connect(client, userdata, flags, reason_code, properties):
        nonlocal connect_failed
        if reason_code.is_failure:
            connect_failed = True
            # e.g. "Not authorized" / "Bad user name or password" -- retrying won't help.
            print(f"Connect failed: {reason_code}", file=sys.stderr)
            client.disconnect()
            return
        print(f"Connected to {args.host}:{args.port}", file=sys.stderr)
        # Subscribing here means subscriptions are restored after auto-reconnect.
        client.subscribe([(topic, args.qos) for topic in args.topics])

    def on_subscribe(client, userdata, mid, reason_codes, properties):
        for topic, rc in zip(args.topics, reason_codes):
            status = "rejected" if rc.is_failure else "ok"
            print(f"Subscribed {topic}: {status} ({rc})", file=sys.stderr)

    last_status = time.monotonic()
    bsm_linked = 0

    def print_status():
        nearest = tracker.bsm_nearest
        near = f"{nearest[0]:.0f} m ({nearest[1]})" if nearest else "n/a (no MAP with a refPoint yet)"
        print(
            f"[status] {received} msgs; {len(tracker.maps)} unique MAP(s); "
            f"{tracker.spat_total} SPaT; {tracker.bsm_total} BSM, {bsm_linked} linked within "
            f"{args.bsm_radius:g} m, {tracker.bsm_no_position} without position; "
            f"closest BSM to a MAP: {near}",
            file=sys.stderr,
            flush=True,
        )

    def on_message(client, userdata, raw_msg):
        nonlocal received, last_status, last_kml_write
        received_at = now_iso()
        payload, _ = normalize_payload(raw_msg.payload)
        msg = SimpleNamespace(topic=raw_msg.topic, payload=payload)
        hex_payload = payload.hex().upper()
        msg_type = msg.topic.split("/", 1)[0].upper()  # "MAP", "SPAT" or "BSM"
        try:
            if msg_type == "MAP":
                handle_map(msg, hex_payload, received_at)
            elif msg_type == "SPAT":
                handle_spat(msg, hex_payload, received_at)
            elif msg_type == "BSM":
                handle_bsm(msg, hex_payload, received_at)
            else:
                emit(f"{received_at} {msg.topic} ({len(msg.payload)} bytes)\n{hex_payload}\n")
        except Exception as exc:  # keep the subscriber alive on unexpected payloads
            print(f"Error handling {msg.topic}: {exc!r}", file=sys.stderr)

        received += 1
        if time.monotonic() - last_status >= 10:
            last_status = time.monotonic()
            print_status()
        if kml and time.monotonic() - last_kml_write >= args.kml_interval:
            last_kml_write = time.monotonic()
            try:
                kml.write(tracker)
            except OSError as exc:
                print(f"Could not write KML: {exc}", file=sys.stderr)
        if args.count and received >= args.count:
            client.disconnect()

    def on_disconnect(client, userdata, flags, reason_code, properties):
        if reason_code != 0:
            print(f"Disconnected ({reason_code}); reconnecting...", file=sys.stderr)

    client = mqtt.Client(
        mqtt.CallbackAPIVersion.VERSION2,
        client_id=args.client_id,
        protocol=mqtt.MQTTv311,
    )
    client.username_pw_set(args.username, args.password)
    if args.tls:
        client.tls_set(cert_reqs=ssl.CERT_NONE if args.insecure else ssl.CERT_REQUIRED)
        client.tls_insecure_set(args.insecure)

    client.on_connect = on_connect
    client.on_subscribe = on_subscribe
    client.on_message = on_message
    client.on_disconnect = on_disconnect
    client.reconnect_delay_set(min_delay=1, max_delay=30)

    try:
        client.connect(args.host, args.port, keepalive=60)
        client.loop_forever()
    except KeyboardInterrupt:
        pass
    except OSError as exc:
        print(f"Could not connect to {args.host}:{args.port}: {exc}", file=sys.stderr)
        sys.exit(1)
    finally:
        client.disconnect()
        if out_file:
            out_file.close()
        if exporter:
            exporter.close()
        print(f"Received {received} messages.", file=sys.stderr)

    if kml:
        kml.write(tracker)
        print(
            f"KML written to {args.kml} ({len(kml.maps)} MAP(s), {len(kml.vehicles)} BSM vehicle(s))",
            file=sys.stderr,
        )
    if received:
        summary = tracker.summary()
        print_summary(summary)
        if args.summary_file:
            with open(args.summary_file, "w", encoding="utf-8") as fh:
                json.dump(summary, fh, indent=2)
            print(f"Summary written to {args.summary_file}", file=sys.stderr)
    if connect_failed:
        sys.exit(2)


if __name__ == "__main__":
    main()
