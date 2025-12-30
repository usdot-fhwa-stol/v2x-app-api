#!/usr/bin/env python3
"""
Standalone MQTT Publisher for V2X Geo Router Testing

This script publishes BSM messages to the ingress topic for geo routing.
It can be used in conjunction with the subscriber script for latency and routing testing.

Usage:
    # Publish a single BSM message
    python scripts/geo_router/publisher.py --device-id 6F9E7F2B --lat 34.0554976 --lon -84.2760438

    # Publish multiple messages at a rate
    python scripts/geo_router/publisher.py --device-id 6F9E7F2B --lat 34.0554976 --lon -84.2760438 --num-messages 100 --rate 10

    # Load test: publish many messages
    python scripts/geo_router/publisher.py --device-id 6F9E7F2B --lat 34.0554976 --lon -84.2760438 --num-messages 1000 --rate 100
"""

import json
import os
import time
import requests
import paho.mqtt.client as mqtt
import argparse
import sys
import uuid
from pathlib import Path
from typing import Optional
from dotenv import load_dotenv

# Load environment variables from .env file in the same directory as this script
script_dir = Path(__file__).parent
env_path = script_dir / ".env"
load_dotenv(dotenv_path=env_path)

# MQTT Configuration
MQTT_BROKER_HOST = os.getenv("MQTT_BROKER_HOST", "localhost")
MQTT_BROKER_PORT = int(os.getenv("MQTT_BROKER_PORT", "1883"))
MQTT_CLIENT_ID_PUBLISHER = "bsm-publisher-standalone"

# Topics
BSM_PUBLISH_TOPIC = "v2x/bsm/publish"  # Ingress: clients publish BSMs here

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
    # Convert device_id from hex string to int
    device_id_int = int(device_id, 16) if isinstance(device_id, str) else device_id

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
    """
    Convert BSM JSON to ASN.1 UPER hex using the serializer API.

    Args:
        bsm_json: JSON string representation of BSM

    Returns:
        Hex string of ASN.1 UPER encoded BSM, or None if conversion fails
    """
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
        print("[Publisher] WARNING: V2X_API_USERNAME and V2X_API_PASSWORD must be set")
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
            print(
                f"[Publisher] Authentication successful (token expires in {expires_in}s)"
            )
            return access_token
        else:
            print("[Publisher] ERROR: No access_token in response")
            return None
    except requests.exceptions.RequestException as e:
        print(f"[Publisher] ERROR: Failed to get access token: {e}")
        if hasattr(e, "response") and e.response is not None:
            try:
                error_text = e.response.text
                print(f"[Publisher] Response: {error_text}")
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
            f"[Publisher] WARNING: Could not get access token, skipping device registration"
        )
        print(
            f"[Publisher] Device location should be registered via REST API for routing to work"
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
            f"[Publisher] Device location registered: {mqtt_client_id} at ({latitude}, {longitude})"
        )
        return True
    except requests.exceptions.RequestException as e:
        print(f"[Publisher] ERROR: Failed to register device location: {e}")
        if hasattr(e, "response") and e.response is not None:
            try:
                error_text = e.response.text
                print(f"[Publisher] Response: {error_text}")
            except:
                pass
        return False


class BSMPublisher:
    """MQTT publisher for BSM messages."""

    def __init__(self, client_id: str):
        self.client_id = client_id
        self.client = None
        self.published_count = 0
        self.publish_timestamps = {}  # message_id -> publish_timestamp

    def on_connect(self, client, userdata, flags, rc, *args, **kwargs):
        """Callback for when the client receives a CONNACK response from the server."""
        if rc == 0:
            print(f"[Publisher] Connected to MQTT broker with result code {rc}")
        else:
            print(f"[Publisher] Failed to connect, return code {rc}")

    def connect(self, host: str, port: int):
        """Connect to MQTT broker."""
        # Try MQTT v5 with callback API v2 (paho-mqtt 2.0+)
        try:
            callback_api = getattr(mqtt, "CallbackAPIVersion", None)
            if callback_api:
                self.client = mqtt.Client(
                    client_id=self.client_id,
                    callback_api_version=callback_api.VERSION2,
                    protocol=mqtt.MQTTv5,
                )
                print("[Publisher] Using MQTT v5 with callback API v2")
            else:
                raise AttributeError("CallbackAPIVersion not available")
        except (AttributeError, TypeError):
            # Fallback: try MQTT v5 with older API
            try:
                self.client = mqtt.Client(
                    client_id=self.client_id, protocol=mqtt.MQTTv5
                )
                print("[Publisher] Using MQTT v5 with legacy API")
            except (AttributeError, TypeError):
                # Fallback to MQTT v3.1.1
                print("[Publisher] MQTT v5 not available, using v3.1.1")
                self.client = mqtt.Client(client_id=self.client_id)

        self.client.on_connect = self.on_connect

        try:
            self.client.connect(host, port, 60)
            self.client.loop_start()
            print(f"[Publisher] Starting publisher loop...")
        except Exception as e:
            print(f"[Publisher] Connection error: {e}")
            raise

    def publish_bsm(
        self, bsm_hex: str, mqtt_client_id: str, message_id: Optional[str] = None
    ):
        """Publish BSM message as hex bytes with MQTT client ID in user properties.

        Args:
            bsm_hex: Hex string of ASN.1 encoded BSM
            mqtt_client_id: MQTT client ID to include in user properties
            message_id: Optional message ID for tracking

        Returns:
            Tuple of (success: bool, message_id: str, publish_time: float)
        """
        if not self.client or not self.client.is_connected():
            print("[Publisher] Client not connected!")
            return False, None, None

        if message_id is None:
            message_id = str(uuid.uuid4())

        try:
            publish_time = time.time()
            payload = hex_to_bytes(bsm_hex)

            # Add MQTT client ID to user properties (MQTT v5)
            # Note: paho-mqtt user properties support varies by version
            try:
                # Try MQTT v5 with Properties object (paho-mqtt 2.0+)
                if hasattr(mqtt, "Properties") and hasattr(mqtt, "PacketTypes"):
                    properties = mqtt.Properties(mqtt.PacketTypes.PUBLISH)
                    properties.UserProperty = [("mqttClientId", mqtt_client_id)]
                    result = self.client.publish(
                        BSM_PUBLISH_TOPIC, payload, qos=1, properties=properties
                    )
                else:
                    # Fallback: publish without user properties
                    # The geo-router will use the connection's client ID if user properties aren't available
                    result = self.client.publish(BSM_PUBLISH_TOPIC, payload, qos=1)
                    if self.published_count == 0:  # Only warn once
                        print(
                            f"[Publisher] NOTE: User properties not available, geo-router will use connection client ID: {self.client_id}"
                        )
            except (AttributeError, TypeError, Exception) as e:
                # Fallback: publish without user properties
                result = self.client.publish(BSM_PUBLISH_TOPIC, payload, qos=1)
                if self.published_count == 0:  # Only warn once
                    print(
                        f"[Publisher] NOTE: Could not add user properties ({e}), using connection client ID: {self.client_id}"
                    )

            result.wait_for_publish()
            self.published_count += 1
            self.publish_timestamps[message_id] = publish_time
            return True, message_id, publish_time
        except Exception as e:
            print(f"[Publisher] Error publishing: {e}")
            return False, message_id, None

    def disconnect(self):
        """Disconnect from MQTT broker."""
        if self.client:
            self.client.loop_stop()
            self.client.disconnect()
            print(
                f"[Publisher] Disconnected. Total messages published: {self.published_count}"
            )


def main():
    """Main function."""
    parser = argparse.ArgumentParser(
        description="Standalone MQTT Publisher for V2X Geo Router Testing",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Publish a single BSM message
  python scripts/geo_router/publisher.py --device-id 6F9E7F2B --lat 34.0554976 --lon -84.2760438

  # Publish multiple messages at 10 msg/s
  python scripts/geo_router/publisher.py --device-id 6F9E7F2B --lat 34.0554976 --lon -84.2760438 --num-messages 100 --rate 10

  # Load test: 1000 messages at 100 msg/s
  python scripts/geo_router/publisher.py --device-id 6F9E7F2B --lat 34.0554976 --lon -84.2760438 --num-messages 1000 --rate 100

  # Custom broker
  MQTT_BROKER_HOST=192.168.1.100 python scripts/geo_router/publisher.py --device-id 6F9E7F2B --lat 34.0554976 --lon -84.2760438
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
        required=True,
        help="Latitude for BSM messages",
    )
    parser.add_argument(
        "--lon",
        type=float,
        required=True,
        help="Longitude for BSM messages",
    )
    parser.add_argument(
        "--num-messages",
        type=int,
        default=1,
        help="Number of BSM messages to publish (default: 1)",
    )
    parser.add_argument(
        "--rate",
        type=float,
        default=1.0,
        help="Messages per second (default: 1.0)",
    )
    parser.add_argument(
        "--speed",
        type=float,
        default=20.0,
        help="Speed in meters per second (default: 20.0)",
    )
    parser.add_argument(
        "--heading",
        type=float,
        default=90.0,
        help="Heading in degrees (default: 90.0)",
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
        "--serializer-url",
        type=str,
        default=None,
        help="Serializer API URL (overrides SERIALIZER_API_URL env var)",
    )
    parser.add_argument(
        "--quiet",
        action="store_true",
        help="Suppress per-message output",
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
    parser.add_argument(
        "--skip-registration",
        action="store_true",
        help="Skip device location registration (for testing without REST API)",
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

    print("=" * 80)
    print("V2X Geo Router Standalone Publisher")
    print("=" * 80)
    print(f"Broker: {MQTT_BROKER_HOST}:{MQTT_BROKER_PORT}")
    print(f"Ingress topic: {BSM_PUBLISH_TOPIC}")
    print(f"Device ID (BSM): {args.device_id}")
    print(f"MQTT Client ID: {mqtt_client_id}")
    print(f"Coordinates: lat={args.lat}, lon={args.lon}")
    print(f"Serializer API: {SERIALIZER_ENDPOINT}")
    print(f"V2X API: {V2X_API_URL}")
    print("=" * 80)

    # Register device location via REST API
    if not args.skip_registration:
        print("\n[Setup] Registering device location via REST API...")
        registration_success = register_device_location(
            mqtt_client_id=mqtt_client_id,
            latitude=args.lat,
            longitude=args.lon,
            elevation=0.0,
            heading=args.heading,
            speed=args.speed,
        )
        if not registration_success:
            print(
                "[Setup] WARNING: Device registration failed. Routing may not work correctly."
            )
            print("[Setup] Make sure V2X_API_TOKEN is set and the API is accessible.")
    else:
        print("\n[Setup] Skipping device registration (--skip-registration)")

    # Check serializer API availability
    print("\n[Setup] Checking serializer API...")
    try:
        response = requests.get(SERIALIZER_API_URL, timeout=5)
        print(f"[Setup] Serializer API is available")
    except requests.exceptions.RequestException as e:
        print(f"[Setup] WARNING: Serializer API not available at {SERIALIZER_API_URL}")
        print(f"[Setup] Error: {e}")
        print("[Setup] Continuing anyway...")

    # Generate BSM messages
    print(f"\n[Setup] Generating {args.num_messages} BSM message(s)...")
    bsm_messages = []
    cached_bsm_hex = None

    for i in range(args.num_messages):
        bsm = create_bsm(
            latitude=args.lat,
            longitude=args.lon,
            msg_count=i + 1,
            device_id=args.device_id,
            speed=args.speed,
            heading=args.heading,
        )
        bsm_json = bsm_to_json(bsm)
        bsm_hex = get_bsm_asn1_hex(bsm_json)

        if bsm_hex:
            if cached_bsm_hex is None:
                cached_bsm_hex = bsm_hex
            bsm_messages.append(bsm_hex)
        elif not args.quiet:
            print(f"[Setup] WARNING: Failed to convert BSM #{i+1} to hex, skipping...")

    if not bsm_messages:
        print("[Setup] ERROR: No valid BSM messages generated!")
        return

    # For load testing, reuse cached BSM
    if args.num_messages > 1:
        print(f"[Setup] Using cached BSM for all {args.num_messages} messages")
        bsm_messages = [cached_bsm_hex] * args.num_messages

    # Create publisher with MQTT client ID
    print("\n[Setup] Creating publisher...")
    publisher = BSMPublisher(mqtt_client_id)  # Use the MQTT client ID for connection
    publisher.connect(MQTT_BROKER_HOST, MQTT_BROKER_PORT)
    time.sleep(1)  # Wait for connection

    # Publish messages
    print(
        f"\n[Publisher] Publishing {len(bsm_messages)} message(s) at {args.rate} msg/s..."
    )
    start_time = time.time()
    interval = 1.0 / args.rate if args.rate > 0 else 0

    for i, bsm_hex in enumerate(bsm_messages):
        message_id = f"msg-{i+1:06d}"
        success, msg_id, publish_time = publisher.publish_bsm(
            bsm_hex, mqtt_client_id, message_id
        )

        if not success and not args.quiet:
            print(f"[Publisher] ERROR: Failed to publish message #{i+1}")

        if not args.quiet and (i < 5 or (i + 1) % 100 == 0):
            print(f"[Publisher] Published message #{i+1}")

        # Rate limiting
        if i < len(bsm_messages) - 1 and interval > 0:
            next_publish_time = start_time + (i + 1) * interval
            sleep_time = next_publish_time - time.time()
            if sleep_time > 0:
                time.sleep(sleep_time)

    publish_duration = time.time() - start_time
    actual_rate = len(bsm_messages) / publish_duration if publish_duration > 0 else 0

    print(
        f"\n[Publisher] Published {len(bsm_messages)} messages in {publish_duration:.2f}s ({actual_rate:.2f} msg/s)"
    )

    # Cleanup
    print("\n[Cleanup] Disconnecting...")
    publisher.disconnect()
    time.sleep(1)

    print("\n" + "=" * 80)
    print("Publisher completed!")
    print("=" * 80)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n[Interrupted] Publisher cancelled by user")
        sys.exit(1)
    except Exception as e:
        print(f"\n\n[Error] Publisher failed: {e}")
        import traceback

        traceback.print_exc()
        sys.exit(1)
