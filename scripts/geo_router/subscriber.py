#!/usr/bin/env python3
"""
Standalone MQTT Subscriber for V2X Geo Router Testing

This script subscribes to client egress topics to receive routed BSM messages.
It can be used in conjunction with the publisher/test script for latency and routing testing.

Usage:
    # Subscribe to a specific device's egress topic
    python scripts/geo_router/subscriber.py --device-id 6F9E7F2B

    # Subscribe and measure latency (requires message timestamps)
    python scripts/geo_router/subscriber.py --device-id 6F9E7F2B --measure-latency

    # Subscribe with verbose output
    python scripts/geo_router/subscriber.py --device-id 6F9E7F2B --verbose
"""

import os
import time
import paho.mqtt.client as mqtt
import argparse
import sys
import statistics
import hashlib
import json
import requests
from pathlib import Path
from typing import Dict, Optional
from datetime import datetime
from dotenv import load_dotenv

# Load environment variables from .env file in the same directory as this script
script_dir = Path(__file__).parent
env_path = script_dir / ".env"
load_dotenv(dotenv_path=env_path)

# MQTT Configuration
MQTT_BROKER_HOST = os.getenv("MQTT_BROKER_HOST", "localhost")
MQTT_BROKER_PORT = int(os.getenv("MQTT_BROKER_PORT", "1883"))
MQTT_CLIENT_ID_SUBSCRIBER = "bsm-subscriber-standalone"

# Topics
BSM_PUBLISH_TOPIC = "v2x/bsm/publish"  # Ingress: publish location here
CLIENT_EGRESS_TOPIC_PATTERN = "v2x/client/{mqttClientId}/messages"  # Egress: receive messages here

# Serializer API
SERIALIZER_API_URL = os.getenv("SERIALIZER_API_URL", "http://localhost:4000")
SERIALIZER_ENDPOINT = f"{SERIALIZER_API_URL}/jer/uper/hex"

# REST API for device registration
V2X_API_URL = os.getenv("V2X_API_URL", "http://localhost:8080")
V2X_API_TOKEN_ENDPOINT = f"{V2X_API_URL}/auth/token"
V2X_API_REGISTER_ENDPOINT = f"{V2X_API_URL}/prd/v2/mqtt/devices/location"
V2X_API_USERNAME = os.getenv("V2X_API_USERNAME", "")
V2X_API_PASSWORD = os.getenv("V2X_API_PASSWORD", "")


def create_bsm(
    latitude: float,
    longitude: float,
    msg_count: int,
    device_id: str,
    elevation: float = -409.6,
    speed: float = None,
    heading: float = None,
):
    """Create a BSM JSON structure."""
    return {
        "messageId": 20,
        "value": {
            "BasicSafetyMessage": {
                "coreData": {
                    "msgCnt": msg_count,
                    "id": device_id,
                    "secMark": 13444,
                    "lat": int(latitude * 10000000),
                    "long": int(longitude * 10000000),
                    "elev": (
                        int(min(elevation * 10, 6143.9))
                        if (elevation > -409.6)
                        else -4096
                    ),
                    "accuracy": {
                        "semiMajor": 255,
                        "semiMinor": 255,
                        "orientation": 65535,
                    },
                    "transmission": "unavailable",
                    "speed": (
                        int(min(speed / 0.02, 8191))
                        if speed is not None
                        else min(int(0 * 50), 8191)
                    ),
                    "heading": int(heading / 0.0125) if heading is not None else 28800,
                    "angle": 127,
                    "accelSet": {"long": 2001, "lat": 2001, "vert": -127, "yaw": 0},
                    "brakes": {
                        "wheelBrakes": "80",
                        "traction": "unavailable",
                        "abs": "unavailable",
                        "scs": "unavailable",
                        "brakeBoost": "unavailable",
                        "auxBrakes": "unavailable",
                    },
                    "size": {"width": 230, "length": 519},
                }
            }
        },
    }


def bsm_to_json(bsm: dict) -> str:
    """Convert BSM dictionary to JSON string."""
    return json.dumps(bsm)


def get_bsm_asn1_hex(bsm_json: str) -> Optional[str]:
    """Convert BSM JSON to ASN.1 UPER hex using the serializer API."""
    try:
        response = requests.post(
            url=SERIALIZER_ENDPOINT,
            data=bsm_json,
            headers={"Content-Type": "application/json"},
            timeout=10,
        )
        response.raise_for_status()
        return response.text.strip()
    except requests.exceptions.RequestException as e:
        print(f"Error calling serializer API: {e}")
        return None


def hex_to_bytes(hex_string: str) -> bytes:
    """Convert hex string to bytes."""
    return bytes.fromhex(hex_string.replace(" ", ""))


def get_access_token(username: str, password: str) -> Optional[str]:
    """Get access token from V2X API using username and password.

    Args:
        username: Username for authentication
        password: Password for authentication

    Returns:
        Access token string if successful, None otherwise
    """
    if not username or not password:
        print("[Subscriber] WARNING: V2X_API_USERNAME and V2X_API_PASSWORD must be set")
        return None

    try:
        payload = {
            "username": username,
            "password": password,
        }
        headers = {"Content-Type": "application/json"}
        response = requests.post(
            V2X_API_TOKEN_ENDPOINT, json=payload, headers=headers, timeout=10
        )
        response.raise_for_status()
        token_data = response.json()
        access_token = token_data.get("access_token")
        if access_token:
            expires_in = token_data.get("expires_in", 0)
            print(f"[Subscriber] Authentication successful (token expires in {expires_in}s)")
            return access_token
        else:
            print("[Subscriber] ERROR: No access_token in response")
            return None
    except requests.exceptions.RequestException as e:
        print(f"[Subscriber] ERROR: Failed to get access token: {e}")
        if hasattr(e, "response") and e.response is not None:
            try:
                error_text = e.response.text
                print(f"[Subscriber] Response: {error_text}")
            except:
                pass
        return None


def register_device_location(
    mqtt_client_id: str,
    latitude: float,
    longitude: float,
    elevation: float = None,
    heading: float = None,
    speed: float = None,
) -> bool:
    """Register or update MQTT device location via REST API.

    Args:
        mqtt_client_id: MQTT client ID
        latitude: Latitude in decimal degrees
        longitude: Longitude in decimal degrees
        elevation: Optional elevation
        heading: Optional heading
        speed: Optional speed

    Returns:
        True if registration successful, False otherwise
    """
    # Get access token first
    access_token = get_access_token(V2X_API_USERNAME, V2X_API_PASSWORD)
    if not access_token:
        print(
            f"[Subscriber] WARNING: Could not get access token, skipping device registration"
        )
        print(
            f"[Subscriber] Device location should be registered via REST API for routing to work"
        )
        return False

    payload = {
        "mqttClientId": mqtt_client_id,
        "latitude": latitude,
        "longitude": longitude,
    }

    if elevation is not None:
        payload["elevation"] = elevation
    if heading is not None:
        payload["heading"] = heading
    if speed is not None:
        payload["speed"] = speed

    try:
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {access_token}",
        }
        response = requests.post(
            V2X_API_REGISTER_ENDPOINT, json=payload, headers=headers, timeout=10
        )
        response.raise_for_status()
        print(
            f"[Subscriber] Device location registered: {mqtt_client_id} at ({latitude}, {longitude})"
        )
        return True
    except requests.exceptions.RequestException as e:
        print(f"[Subscriber] ERROR: Failed to register device location: {e}")
        if hasattr(e, "response") and e.response is not None:
            try:
                error_text = e.response.text
                print(f"[Subscriber] Response: {error_text}")
            except:
                pass
        return False


class BSMSubscriber:
    """Standalone MQTT subscriber for client egress topics."""

    def __init__(
        self,
        client_id: str,
        mqtt_client_id: str,
        latitude: float = None,
        longitude: float = None,
        measure_latency: bool = False,
        verbose: bool = False,
    ):
        """Initialize the subscriber.

        Args:
            client_id: MQTT connection client ID
            mqtt_client_id: MQTT client ID for routing (used in subscription topic)
            measure_latency: If True, attempt to measure latency from message timestamps
            verbose: If True, print detailed message information
        """
        self.client_id = client_id
        self.mqtt_client_id = mqtt_client_id  # This is the ID used for routing
        self.latitude = latitude
        self.longitude = longitude
        self.measure_latency = measure_latency
        self.verbose = verbose
        self.client = None
        self.publisher_client = None  # For publishing location
        self.received_messages = []
        self.message_count = 0
        self.duplicate_count = 0
        self.seen_messages = {}  # topic:payload_hash -> first_receive_timestamp
        self.latencies = []  # List of latency measurements in seconds
        self.start_time = None
        self.last_message_time = None

    def register_location(self):
        """Register device location via REST API."""
        if self.latitude is None or self.longitude is None:
            print("[Subscriber] WARNING: No location provided, skipping location registration")
            return False

        print(f"[Subscriber] Registering device location via REST API...")
        print(f"  MQTT Client ID: {self.mqtt_client_id}")
        print(f"  Location: lat={self.latitude}, lon={self.longitude}")

        return register_device_location(
            mqtt_client_id=self.mqtt_client_id,
            latitude=self.latitude,
            longitude=self.longitude,
            elevation=0.0,
            heading=90.0,
            speed=20.0
        )

    def on_connect(self, client, userdata, flags, rc, *args, **kwargs):
        """Callback for when the client receives a CONNACK response from the server."""
        if rc == 0:
            print(f"[Subscriber] Connected to MQTT broker with result code {rc}")
            # Subscribe to client egress topic using MQTT client ID
            topic = CLIENT_EGRESS_TOPIC_PATTERN.format(mqttClientId=self.mqtt_client_id)
            result = client.subscribe(topic, qos=1)
            print(f"[Subscriber] Subscribed to: {topic}")
            if result[0] == mqtt.MQTT_ERR_SUCCESS:
                print(f"[Subscriber] Subscribe successful, mid: {result[1]}")
            else:
                print(f"[Subscriber] Subscribe failed with code: {result[0]}")
            self.start_time = time.time()
        else:
            print(f"[Subscriber] Failed to connect, return code {rc}")

    def on_message(self, client, userdata, msg):
        """Callback for when a PUBLISH message is received from the server."""
        topic = msg.topic
        payload = msg.payload
        receive_time = time.time()
        self.last_message_time = receive_time

        # Create payload hash for tracking
        payload_hash = hashlib.md5(payload).hexdigest()

        # Create a unique key: topic + payload hash
        dedup_key = f"{topic}:{payload_hash}"

        message_info = {
            "topic": topic,
            "payload_size": len(payload),
            "payload_hash": payload_hash,
            "payload_hex": (
                payload.hex()[:50] + "..." if len(payload) > 25 else payload.hex()
            ),
            "timestamp": receive_time,
            "datetime": datetime.fromtimestamp(receive_time).strftime(
                "%Y-%m-%d %H:%M:%S.%f"
            ),
        }

        # Check if we've seen this exact topic+payload combination before
        if dedup_key in self.seen_messages:
            # True duplicate: same topic + same payload = already counted
            self.duplicate_count += 1
            if self.verbose:
                print(f"[Subscriber] Duplicate message detected (hash: {payload_hash[:8]}...)")
        else:
            # Unique: first time seeing this topic+payload combination
            self.seen_messages[dedup_key] = receive_time
            self.message_count += 1
            self.received_messages.append(message_info)

            # Print message info
            elapsed = receive_time - self.start_time if self.start_time else 0
            print(
                f"\n[Subscriber] Received message #{self.message_count} (elapsed: {elapsed:.3f}s)"
            )
            print(f"  Topic: {topic}")
            print(f"  Payload size: {len(payload)} bytes")
            print(f"  Payload hash: {payload_hash[:16]}...")
            print(f"  Time: {message_info['datetime']}")

            if self.verbose:
                print(f"  Payload (hex preview): {message_info['payload_hex']}")

            # Attempt to measure latency if enabled
            if self.measure_latency:
                # Note: Latency measurement requires message timestamps in the payload
                # This is a placeholder - actual implementation would decode the BSM
                # and extract publish timestamp if available
                pass

    def on_subscribe(self, client, userdata, mid, granted_qos, *args, **kwargs):
        """Callback for when the broker responds to a subscribe request."""
        if isinstance(granted_qos, list):
            qos_list = granted_qos
        elif isinstance(granted_qos, tuple):
            qos_list = list(granted_qos)
        else:
            qos_list = [granted_qos]
        print(f"[Subscriber] Subscribed with QoS: {qos_list}")

    def get_statistics(self) -> Dict:
        """Get message statistics."""
        total_received = self.message_count + self.duplicate_count
        duration = (
            (self.last_message_time - self.start_time)
            if self.start_time and self.last_message_time
            else 0
        )
        receive_rate = total_received / duration if duration > 0 else 0

        stats = {
            "message_count": self.message_count,
            "duplicate_count": self.duplicate_count,
            "total_received": total_received,
            "duration": duration,
            "receive_rate": receive_rate,
        }

        if self.latencies:
            sorted_latencies = sorted(self.latencies)
            stats.update(
                {
                    "latency_count": len(self.latencies),
                    "latency_min": min(self.latencies),
                    "latency_max": max(self.latencies),
                    "latency_mean": statistics.mean(self.latencies),
                    "latency_median": statistics.median(self.latencies),
                    "latency_p50": (
                        sorted_latencies[int(len(sorted_latencies) * 0.50)]
                        if sorted_latencies
                        else None
                    ),
                    "latency_p95": (
                        sorted_latencies[int(len(sorted_latencies) * 0.95)]
                        if len(sorted_latencies) > 1
                        else None
                    ),
                    "latency_p99": (
                        sorted_latencies[int(len(sorted_latencies) * 0.99)]
                        if len(sorted_latencies) > 1
                        else None
                    ),
                    "latency_stddev": (
                        statistics.stdev(self.latencies)
                        if len(self.latencies) > 1
                        else None
                    ),
                }
            )

        return stats

    def connect(self, host: str, port: int):
        """Connect to MQTT broker and register location."""
        # First, register location via REST API so geo router is aware
        if self.latitude is not None and self.longitude is not None:
            self.register_location()

        # Try MQTT v5 with callback API v2 (paho-mqtt 2.0+)
        try:
            callback_api = getattr(mqtt, "CallbackAPIVersion", None)
            if callback_api:
                self.client = mqtt.Client(
                    client_id=self.client_id,
                    callback_api_version=callback_api.VERSION2,
                    protocol=mqtt.MQTTv5,
                )
                print("[Subscriber] Using MQTT v5 with callback API v2")
            else:
                raise AttributeError("CallbackAPIVersion not available")
        except (AttributeError, TypeError):
            # Fallback: try MQTT v5 with older API
            try:
                self.client = mqtt.Client(
                    client_id=self.client_id, protocol=mqtt.MQTTv5
                )
                print("[Subscriber] Using MQTT v5 with legacy API")
            except (AttributeError, TypeError):
                # Fallback to MQTT v3.1.1
                print("[Subscriber] MQTT v5 not available, using v3.1.1")
                self.client = mqtt.Client(client_id=self.client_id)

        self.client.on_connect = self.on_connect
        self.client.on_message = self.on_message
        self.client.on_subscribe = self.on_subscribe

        try:
            self.client.connect(host, port, 60)
            self.client.loop_start()
            print(f"[Subscriber] Starting subscriber loop...")
        except Exception as e:
            print(f"[Subscriber] Connection error: {e}")
            raise

    def disconnect(self):
        """Disconnect from MQTT broker."""
        if self.client:
            self.client.loop_stop()
            self.client.disconnect()
            total_received = self.message_count + self.duplicate_count
            print(
                f"\n[Subscriber] Disconnected. Unique messages: {self.message_count}, "
                f"Duplicates: {self.duplicate_count}, Total received: {total_received}"
            )


def main():
    """Main function."""
    parser = argparse.ArgumentParser(
        description="Standalone MQTT Subscriber for V2X Geo Router Testing",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Subscribe to a specific device's egress topic
  python scripts/geo_router/subscriber.py --device-id 6F9E7F2B

  # Subscribe with verbose output
  python scripts/geo_router/subscriber.py --device-id 6F9E7F2B --verbose

  # Subscribe and measure latency
  python scripts/geo_router/subscriber.py --device-id 6F9E7F2B --measure-latency

  # Subscribe with custom broker
  MQTT_BROKER_HOST=192.168.1.100 python scripts/geo_router/subscriber.py --device-id 6F9E7F2B
        """,
    )
    parser.add_argument(
        "--device-id",
        type=str,
        required=True,
        help="Device ID (hex string, e.g., 6F9E7F2B) - used in BSM",
    )
    parser.add_argument(
        "--mqtt-client-id",
        type=str,
        default=None,
        help="MQTT client ID for routing (default: uses --device-id)",
    )
    parser.add_argument(
        "--lat",
        type=float,
        default=None,
        help="Latitude for location publish (required for geo router awareness)",
    )
    parser.add_argument(
        "--lon",
        type=float,
        default=None,
        help="Longitude for location publish (required for geo router awareness)",
    )
    parser.add_argument(
        "--measure-latency",
        action="store_true",
        help="Attempt to measure latency from message timestamps",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Print detailed message information",
    )
    parser.add_argument(
        "--broker-host",
        type=str,
        default=None,
        help="MQTT broker host (overrides MQTT_BROKER_HOST env var)",
    )
    parser.add_argument(
        "--broker-port",
        type=int,
        default=None,
        help="MQTT broker port (overrides MQTT_BROKER_PORT env var)",
    )
    parser.add_argument(
        "--duration",
        type=float,
        default=None,
        help="Duration to run subscriber in seconds (default: run until interrupted)",
    )
    parser.add_argument(
        "--serializer-url",
        type=str,
        default=None,
        help="Serializer API URL (overrides SERIALIZER_API_URL env var)",
    )
    parser.add_argument(
        "--no-location-publish",
        action="store_true",
        help="Skip registering location on startup",
    )
    parser.add_argument(
        "--api-url",
        type=str,
        default=None,
        help="V2X API URL for device registration (overrides V2X_API_URL env var)",
    )
    parser.add_argument(
        "--api-username",
        type=str,
        default=None,
        help="V2X API username for authentication (overrides V2X_API_USERNAME env var)",
    )
    parser.add_argument(
        "--api-password",
        type=str,
        default=None,
        help="V2X API password for authentication (overrides V2X_API_PASSWORD env var)",
    )

    args = parser.parse_args()
    
    # Determine MQTT client ID
    mqtt_client_id = args.mqtt_client_id if args.mqtt_client_id else args.device_id

    # Override environment variables if provided
    global MQTT_BROKER_HOST, MQTT_BROKER_PORT, SERIALIZER_ENDPOINT, V2X_API_URL, V2X_API_TOKEN_ENDPOINT, V2X_API_REGISTER_ENDPOINT, V2X_API_USERNAME, V2X_API_PASSWORD
    if args.broker_host:
        MQTT_BROKER_HOST = args.broker_host
    if args.broker_port:
        MQTT_BROKER_PORT = args.broker_port
    if args.serializer_url:
        SERIALIZER_ENDPOINT = f"{args.serializer_url}/jer/uper/hex"
    if args.api_url:
        V2X_API_URL = args.api_url
        V2X_API_TOKEN_ENDPOINT = f"{V2X_API_URL}/auth/token"
        V2X_API_REGISTER_ENDPOINT = f"{V2X_API_URL}/prd/v2/mqtt/devices/location"
    if args.api_username:
        V2X_API_USERNAME = args.api_username
    if args.api_password:
        V2X_API_PASSWORD = args.api_password

    # Validate location if not skipping location publish
    if not args.no_location_publish and (args.lat is None or args.lon is None):
        print("ERROR: --lat and --lon are required unless --no-location-publish is used")
        print("The geo router needs to know your device location to route messages to you.")
        sys.exit(1)

    print("=" * 80)
    print("V2X Geo Router Standalone Subscriber")
    print("=" * 80)
    print(f"Broker: {MQTT_BROKER_HOST}:{MQTT_BROKER_PORT}")
    print(f"Device ID (BSM): {args.device_id}")
    print(f"MQTT Client ID: {mqtt_client_id}")
    print(f"Client egress topic: {CLIENT_EGRESS_TOPIC_PATTERN.format(mqttClientId=mqtt_client_id)}")
    if args.lat is not None and args.lon is not None:
        print(f"Location: lat={args.lat}, lon={args.lon}")
    print(f"V2X API: {V2X_API_URL}")
    if args.no_location_publish:
        print("WARNING: Location registration disabled - geo router may not route messages to this device")
    print("=" * 80)

    # Create subscriber
    subscriber = BSMSubscriber(
        client_id=mqtt_client_id,  # Use MQTT client ID for connection
        mqtt_client_id=mqtt_client_id,  # Use for routing
        latitude=args.lat if not args.no_location_publish else None,
        longitude=args.lon if not args.no_location_publish else None,
        measure_latency=args.measure_latency,
        verbose=args.verbose,
    )

    try:
        subscriber.connect(MQTT_BROKER_HOST, MQTT_BROKER_PORT)
        time.sleep(1)  # Wait for connection

        print("\n[Subscriber] Waiting for messages...")
        print("[Subscriber] Press Ctrl+C to stop\n")

        # Run for specified duration or until interrupted
        if args.duration:
            time.sleep(args.duration)
        else:
            # Run until interrupted
            try:
                while True:
                    time.sleep(1)
            except KeyboardInterrupt:
                print("\n[Subscriber] Interrupted by user")

    except KeyboardInterrupt:
        print("\n[Subscriber] Interrupted by user")
    except Exception as e:
        print(f"\n[Subscriber] Error: {e}")
        import traceback

        traceback.print_exc()
        sys.exit(1)
    finally:
        # Print statistics
        stats = subscriber.get_statistics()
        print("\n" + "=" * 80)
        print("Subscriber Statistics")
        print("=" * 80)
        print(f"Unique messages:      {stats['message_count']}")
        print(f"Duplicate messages:   {stats['duplicate_count']}")
        print(f"Total received:       {stats['total_received']}")
        print(f"Duration:             {stats['duration']:.2f} seconds")
        print(f"Receive rate:         {stats['receive_rate']:.2f} msg/s")

        if stats.get("latency_count", 0) > 0:
            print("\n" + "-" * 80)
            print("Latency Statistics")
            print("-" * 80)
            print(f"  Samples: {stats['latency_count']}")
            print(f"  Min:     {stats['latency_min']*1000:.2f} ms")
            print(f"  Max:     {stats['latency_max']*1000:.2f} ms")
            print(f"  Mean:    {stats['latency_mean']*1000:.2f} ms")
            print(f"  Median:  {stats['latency_median']*1000:.2f} ms")
            if stats.get("latency_p50"):
                print(f"  P50:     {stats['latency_p50']*1000:.2f} ms")
            if stats.get("latency_p95"):
                print(f"  P95:     {stats['latency_p95']*1000:.2f} ms")
            if stats.get("latency_p99"):
                print(f"  P99:     {stats['latency_p99']*1000:.2f} ms")
            if stats.get("latency_stddev"):
                print(f"  StdDev:  {stats['latency_stddev']*1000:.2f} ms")

        subscriber.disconnect()
        print("\n" + "=" * 80)
        print("Subscriber stopped")
        print("=" * 80)


if __name__ == "__main__":
    main()

