#!/usr/bin/env python3
"""
Test script for V2X Geo Router System

This script:
1. Creates BSM messages with geographic coordinates
2. Converts them to ASN.1 UPER hex format using the serializer API
3. Publishes to MQTT broker on v2x/bsm/publish
4. Subscribes to geo-relevance topics to receive routed messages
5. Validates the routing functionality
"""

import json
import os
import time
import requests
import paho.mqtt.client as mqtt
from pathlib import Path
from typing import Optional, Dict, List
import argparse
import sys
import uuid
import statistics
import hashlib
from collections import defaultdict
from dotenv import load_dotenv

# Load environment variables from .env file in the same directory as this script
script_dir = Path(__file__).parent
env_path = script_dir / ".env"
load_dotenv(dotenv_path=env_path)

# MQTT Configuration
MQTT_BROKER_HOST = os.getenv("MQTT_BROKER_HOST", "localhost")
MQTT_BROKER_PORT = int(os.getenv("MQTT_BROKER_PORT", "1883"))
MQTT_CLIENT_ID_PUBLISHER = "bsm-publisher-test"
MQTT_CLIENT_ID_SUBSCRIBER = "bsm-subscriber-test"

# Topics
BSM_PUBLISH_TOPIC = "v2x/bsm/publish"  # Ingress: clients publish BSMs here
CLIENT_EGRESS_TOPIC_PATTERN = (
    "v2x/client/{deviceId}/messages"  # Egress: clients receive routed messages here
)

# Serializer API
SERIALIZER_API_URL = os.getenv("SERIALIZER_API_URL", "http://localhost:4000")
SERIALIZER_ENDPOINT = f"{SERIALIZER_API_URL}/jer/uper/hex"


def create_bsm(
    latitude: float,
    longitude: float,
    msg_count: int,
    elevation: float = -409.6,
    speed: float = None,
    heading: float = None,
    id: int = None,
):
    """Create a BSM JSON structure."""
    device_id = int.from_bytes(os.urandom(4), "big") if id is None else id
    return {
        "messageId": 20,
        "value": {
            "BasicSafetyMessage": {
                "coreData": {
                    "msgCnt": msg_count,
                    "id": f"{format(device_id, '08X')}",
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


class BSMSubscriber:
    """MQTT subscriber for client egress topics (direct client routing)."""

    def __init__(self, client_id: str, device_id: Optional[str] = None):
        """Initialize the subscriber.

        Args:
            client_id: MQTT client ID
            device_id: Device ID to subscribe to client egress topic (if None, will extract from first BSM)
        """
        self.client_id = client_id
        self.device_id = (
            device_id  # Will be set when we receive first BSM or from parameter
        )
        self.client = None
        self.received_messages = []
        self.message_count = 0
        self.duplicate_count = 0  # Count of true duplicates (same topic+payload)
        self.seen_messages = (
            {}
        )  # topic:payload_hash -> first_receive_timestamp (for deduplication)
        self.seen_payloads = (
            {}
        )  # payload_hash -> list of receive_timestamps (for latency matching)
        self.latencies = []  # List of latency measurements in seconds
        self.message_timestamps = {}  # message_id -> receive_timestamp

    def on_connect(self, client, userdata, flags, rc, *args, **kwargs):
        """Callback for when the client receives a CONNACK response from the server."""
        if rc == 0:
            print(f"[Subscriber] Connected to MQTT broker with result code {rc}")
            # Subscribe to client egress topic if device_id is known
            if self.device_id:
                topic = CLIENT_EGRESS_TOPIC_PATTERN.format(deviceId=self.device_id)
                result = client.subscribe(topic, qos=1)
                print(f"[Subscriber] Subscribed to: {topic}")
                if result[0] == mqtt.MQTT_ERR_SUCCESS:
                    print(f"[Subscriber] Subscribe successful, mid: {result[1]}")
                else:
                    print(f"[Subscriber] Subscribe failed with code: {result[0]}")
            else:
                print(
                    f"[Subscriber] Device ID not set, will subscribe after receiving first BSM"
                )
        else:
            print(f"[Subscriber] Failed to connect, return code {rc}")

    def on_message(self, client, userdata, msg):
        """Callback for when a PUBLISH message is received from the server."""
        topic = msg.topic
        payload = msg.payload
        receive_time = time.time()

        # Create payload hash for tracking
        payload_hash = hashlib.md5(payload).hexdigest()

        # Create a unique key: topic + payload hash
        # This allows us to deduplicate same payload on same topic (true duplicates)
        # but count same payload on different topics as separate (from different geohash publishes)
        dedup_key = f"{topic}:{payload_hash}"

        message_info = {
            "topic": topic,
            "payload_size": len(payload),
            "payload_hash": payload_hash,
            "payload_hex": (
                payload.hex()[:50] + "..." if len(payload) > 25 else payload.hex()
            ),
            "timestamp": receive_time,
        }

        # Check if we've seen this exact topic+payload combination before
        if dedup_key in self.seen_messages:
            # True duplicate: same topic + same payload = already counted
            self.duplicate_count += 1
        else:
            # Unique: first time seeing this topic+payload combination
            self.seen_messages[dedup_key] = receive_time
            self.message_count += 1
            self.received_messages.append(message_info)

            # Also track by payload hash for latency matching
            if payload_hash not in self.seen_payloads:
                self.seen_payloads[payload_hash] = []

            print(f"\n[Subscriber] Received message #{self.message_count}")
            print(f"  Topic: {topic}")
            print(f"  Payload size: {len(payload)} bytes")
            print(f"  Payload (hex preview): {message_info['payload_hex']}")

    def record_latency(self, message_id: str, publish_time: float, receive_time: float):
        """Record latency for a message."""
        latency = receive_time - publish_time
        # Only record positive latencies (receive should be after publish)
        if latency > 0:
            self.latencies.append(latency)
            self.message_timestamps[message_id] = receive_time
        # If latency is negative or zero, skip it (indicates matching error)

    def get_statistics(self) -> Dict:
        """Get latency statistics."""
        if not self.latencies:
            return {
                "count": 0,
                "min": None,
                "max": None,
                "mean": None,
                "median": None,
                "p50": None,
                "p95": None,
                "p99": None,
                "stddev": None,
            }

        sorted_latencies = sorted(self.latencies)
        return {
            "count": len(self.latencies),
            "min": min(self.latencies),
            "max": max(self.latencies),
            "mean": statistics.mean(self.latencies),
            "median": statistics.median(self.latencies),
            "p50": (
                sorted_latencies[int(len(sorted_latencies) * 0.50)]
                if sorted_latencies
                else None
            ),
            "p95": (
                sorted_latencies[int(len(sorted_latencies) * 0.95)]
                if len(sorted_latencies) > 1
                else None
            ),
            "p99": (
                sorted_latencies[int(len(sorted_latencies) * 0.99)]
                if len(sorted_latencies) > 1
                else None
            ),
            "stddev": (
                statistics.stdev(self.latencies) if len(self.latencies) > 1 else None
            ),
        }

    def on_subscribe(self, client, userdata, mid, granted_qos, *args, **kwargs):
        """Callback for when the broker responds to a subscribe request."""
        if isinstance(granted_qos, list):
            qos_list = granted_qos
        elif isinstance(granted_qos, tuple):
            qos_list = list(granted_qos)
        else:
            qos_list = [granted_qos]
        print(f"[Subscriber] Subscribed with QoS: {qos_list}")

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
                f"[Subscriber] Disconnected. Unique messages: {self.message_count}, "
                f"Duplicates: {self.duplicate_count}, Total received: {total_received}"
            )


class BSMPublisher:
    """MQTT publisher for BSM messages."""

    def __init__(self, client_id: str):
        self.client_id = client_id
        self.client = None
        self.published_count = 0
        self.publish_timestamps = {}  # message_id -> publish_timestamp
        self.message_ids = []  # List of message IDs in publish order

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

    def publish_bsm(self, bsm_hex: str, message_id: Optional[str] = None):
        """Publish BSM message as hex bytes.

        Args:
            bsm_hex: Hex string of ASN.1 encoded BSM
            message_id: Optional message ID for tracking latency

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
            result = self.client.publish(BSM_PUBLISH_TOPIC, payload, qos=1)
            result.wait_for_publish()
            self.published_count += 1
            self.publish_timestamps[message_id] = publish_time
            self.message_ids.append(message_id)
            # Minimal output - detailed output controlled by caller
            if self.published_count % 100 == 0 or self.published_count <= 5:
                print(f"[Publisher] Published {self.published_count} messages...")
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


def test_geo_routing(
    latitude: float,
    longitude: float,
    num_messages: int = 1,
    delay: float = 2.0,
    load_test: bool = False,
    messages_per_second: float = 1.0,
    quiet: bool = False,
):
    """
    Test the geo routing system by publishing BSM messages and subscribing to routed messages.

    Args:
        latitude: Latitude for BSM messages
        longitude: Longitude for BSM messages
        num_messages: Number of BSM messages to publish
        delay: Delay after publishing completes before checking results (seconds)
        load_test: If True, run load test mode (suppress per-message output)
        messages_per_second: Rate for load testing (messages per second)
        quiet: If True, suppress detailed per-message output
    """
    print("=" * 80)
    print("V2X Geo Router Test Script")
    print("=" * 80)
    print(f"Broker: {MQTT_BROKER_HOST}:{MQTT_BROKER_PORT}")
    print(f"Ingress topic: {BSM_PUBLISH_TOPIC}")
    print(f"Client egress topic pattern: {CLIENT_EGRESS_TOPIC_PATTERN}")
    print(f"Serializer API: {SERIALIZER_ENDPOINT}")
    print(f"Test coordinates: lat={latitude}, lon={longitude}")
    print("=" * 80)

    # Check serializer API availability
    print("\n[Setup] Checking serializer API...")
    try:
        response = requests.get(SERIALIZER_API_URL, timeout=5)
        print(f"[Setup] Serializer API is available")
    except requests.exceptions.RequestException as e:
        print(f"[Setup] WARNING: Serializer API not available at {SERIALIZER_API_URL}")
        print(f"[Setup] Error: {e}")
        print("[Setup] Continuing anyway...")

    # Pre-generate BSM messages and extract device ID first
    device_id = None
    if load_test:
        if not quiet:
            print(f"\n[Test] Pre-generating BSM message (will be cached and reused)...")

        # For load testing, generate one BSM and cache the UPER hex
        bsm = create_bsm(
            latitude=latitude,
            longitude=longitude,
            msg_count=1,
            speed=20.0,
            heading=90.0,
        )
        # Extract device ID from BSM
        device_id = bsm["value"]["BasicSafetyMessage"]["coreData"]["id"]
        bsm_json = bsm_to_json(bsm)
        cached_bsm_hex = get_bsm_asn1_hex(bsm_json)

        if not cached_bsm_hex:
            print("[Test] ERROR: Failed to convert BSM to hex!")
            return

        if not quiet:
            print(f"[Test] BSM UPER hex cached ({len(cached_bsm_hex)} characters)")
            print(f"[Test] Device ID: {device_id}")
            print(f"[Test] Will reuse cached BSM for all {num_messages} messages")

        # Reuse the cached BSM hex for all messages
        bsm_messages = [cached_bsm_hex] * num_messages
    else:
        # For non-load-test mode, generate unique BSM messages
        if not quiet:
            print(f"\n[Test] Generating {num_messages} BSM messages...")

        bsm_messages = []
        for i in range(num_messages):
            bsm = create_bsm(
                latitude=latitude,
                longitude=longitude,
                msg_count=i + 1,
                speed=20.0,
                heading=90.0,
            )
            # Extract device ID from first BSM
            if device_id is None:
                device_id = bsm["value"]["BasicSafetyMessage"]["coreData"]["id"]
            bsm_json = bsm_to_json(bsm)
            bsm_hex = get_bsm_asn1_hex(bsm_json)
            if bsm_hex:
                bsm_messages.append(bsm_hex)
            elif not quiet:
                print(
                    f"[Test] WARNING: Failed to convert BSM #{i+1} to hex, skipping..."
                )

    if not bsm_messages:
        print("[Test] ERROR: No valid BSM messages generated!")
        return

    if not device_id:
        print("[Test] ERROR: Could not extract device ID from BSM!")
        return

    # Create subscriber with device ID
    print("\n[Setup] Creating subscriber...")
    subscriber = BSMSubscriber(MQTT_CLIENT_ID_SUBSCRIBER, device_id=device_id)
    subscriber.connect(MQTT_BROKER_HOST, MQTT_BROKER_PORT)
    time.sleep(1)  # Wait for connection

    # Create publisher
    print("\n[Setup] Creating publisher...")
    publisher = BSMPublisher(MQTT_CLIENT_ID_PUBLISHER)
    publisher.connect(MQTT_BROKER_HOST, MQTT_BROKER_PORT)
    time.sleep(1)  # Wait for connection

    # Publish BSM messages to ingress (to cache device location)
    if load_test:
        print(
            f"\n[Test] Load Testing: Publishing {len(bsm_messages)} messages at {messages_per_second} msg/s..."
        )
    else:
        print(f"\n[Test] Publishing {len(bsm_messages)} BSM message(s)...")

    start_time = time.time()
    interval = 1.0 / messages_per_second if messages_per_second > 0 else 0

    for i, bsm_hex in enumerate(bsm_messages):
        message_id = f"msg-{i+1:06d}"

        if not quiet and not load_test:
            print(f"\n[Test] Publishing message #{i+1}...")

        success, msg_id, publish_time = publisher.publish_bsm(bsm_hex, message_id)

        if not success and not quiet:
            print(f"[Test] ERROR: Failed to publish message #{i+1}")

        # Rate limiting for load testing
        if i < len(bsm_messages) - 1 and interval > 0:
            next_publish_time = start_time + (i + 1) * interval
            sleep_time = next_publish_time - time.time()
            if sleep_time > 0:
                time.sleep(sleep_time)

    publish_duration = time.time() - start_time
    actual_rate = len(bsm_messages) / publish_duration if publish_duration > 0 else 0

    if load_test:
        print(
            f"[Test] Published {len(bsm_messages)} messages to ingress in {publish_duration:.2f}s ({actual_rate:.2f} msg/s)"
        )

    # Wait a bit for geo router to cache device location and route messages
    print(
        f"\n[Test] Waiting 1 second for geo router to cache device location and route messages..."
    )
    time.sleep(1)

    # Wait for routing and collect latency data
    if load_test:
        print(f"\n[Test] Waiting {delay} seconds for all messages to be routed...")
    else:
        print(
            f"\n[Test] Waiting {delay} seconds for geo router to process and route messages..."
        )

    # Match received messages with published messages for latency calculation
    # Since we can't easily match by content, we'll use sequence-based matching
    # For load testing, we'll calculate latency based on first/last message timing
    wait_start = time.time()
    time.sleep(delay)

    # Additional wait disabled - rely on the delay parameter instead

    # Match messages for latency calculation
    # In load test mode: All messages have same payload, but different publish times
    # Strategy: Group received messages by payload hash, use first receive time for each group
    # Then match by order (assuming messages arrive in publish order)

    matched_count = 0

    if load_test:
        # In load test mode, all messages have same payload hash (cached BSM)
        # Strategy: Match by order - messages arrive roughly in publish order
        # Each published message results in ~9 received messages (one per geohash topic)
        # We'll use the FIRST receive time for each batch of ~9 messages

        # Sort received messages by timestamp to ensure chronological order
        sorted_received = sorted(
            subscriber.received_messages, key=lambda x: x["timestamp"]
        )

        if len(sorted_received) > 0 and publisher.published_count > 0:
            # Match each published message to the first received message that arrives after it
            # Since messages arrive roughly in order, we can use a sliding window approach
            last_matched_idx = 0

            for i, msg_id in enumerate(publisher.message_ids):
                publish_time = publisher.publish_timestamps.get(msg_id)
                if not publish_time:
                    continue

                # Find the first received message after this publish time
                # Start from where we left off (messages arrive roughly in order)
                matched = False
                for j in range(last_matched_idx, len(sorted_received)):
                    receive_time = sorted_received[j]["timestamp"]
                    if receive_time > publish_time:
                        # Found the first message after publish - use it
                        subscriber.record_latency(msg_id, publish_time, receive_time)
                        matched_count += 1
                        last_matched_idx = (
                            j + 1
                        )  # Next publish should start searching from here
                        matched = True
                        break

                if not matched:
                    # No message found after this publish time - might be still processing
                    # Skip this match
                    pass
    else:
        # Normal mode: Match by order (each published message = one received message)
        for i, msg_id in enumerate(publisher.message_ids):
            if i < len(subscriber.received_messages):
                receive_time = subscriber.received_messages[i]["timestamp"]
                publish_time = publisher.publish_timestamps.get(msg_id)
                if publish_time:
                    subscriber.record_latency(msg_id, publish_time, receive_time)
                    matched_count += 1

    if load_test and matched_count < min(
        len(publisher.message_ids), len(subscriber.received_messages)
    ):
        print(
            f"[Test] WARNING: Only matched {matched_count}/{min(len(publisher.message_ids), len(subscriber.received_messages))} messages for latency calculation"
        )

    # Check results
    print("\n" + "=" * 80)
    print("Test Results")
    print("=" * 80)
    print(f"Messages published: {publisher.published_count}")
    print(f"Unique topic+payload combinations: {subscriber.message_count}")
    total_received = subscriber.message_count + subscriber.duplicate_count
    print(f"Total messages received: {total_received}")
    if subscriber.duplicate_count > 0:
        print(f"Duplicate messages (same topic+payload): {subscriber.duplicate_count}")

    # Calculate success rate
    # With direct client routing: Each published message routes to relevant clients
    # In load test mode: All messages have same payload, but different publish times
    # Each published message routes to multiple clients (one per relevant device)
    if load_test:
        # In load test, we expect to receive messages from other devices
        # Success rate = (total_received / published_count) * 100
        # This represents how many of our published messages resulted in received messages
        success_rate = (
            (total_received / publisher.published_count * 100)
            if publisher.published_count > 0
            else 0
        )
        print(
            f"Success rate: {success_rate:.1f}% (messages received / messages published)"
        )

        if subscriber.message_count > 0:
            avg_messages_per_publish = (
                total_received / publisher.published_count
                if publisher.published_count > 0
                else 0
            )
            print(
                f"Average messages received per published message: {avg_messages_per_publish:.1f}"
            )
    else:
        # Normal mode: One published message may route to multiple clients
        # Success rate based on whether we received at least one message
        success_rate = (1.0 if subscriber.message_count > 0 else 0.0) * 100
        print(f"Success rate: {success_rate:.1f}% (at least one message received)")

    if subscriber.message_count > 0:
        print("\n✅ SUCCESS: Geo router is working!")

        # Print latency statistics if available
        stats = subscriber.get_statistics()
        if stats["count"] > 0:
            print("\n" + "-" * 80)
            print("Latency Statistics (publish → receive)")
            print("-" * 80)
            print(f"  Samples: {stats['count']}")
            print(f"  Min:     {stats['min']*1000:.2f} ms")
            print(f"  Max:     {stats['max']*1000:.2f} ms")
            print(f"  Mean:    {stats['mean']*1000:.2f} ms")
            print(f"  Median:  {stats['median']*1000:.2f} ms")
            if stats["p50"]:
                print(f"  P50:     {stats['p50']*1000:.2f} ms")
            if stats["p95"]:
                print(f"  P95:     {stats['p95']*1000:.2f} ms")
            if stats["p99"]:
                print(f"  P99:     {stats['p99']*1000:.2f} ms")
            if stats["stddev"]:
                print(f"  StdDev:  {stats['stddev']*1000:.2f} ms")

        if not quiet and not load_test:
            print("\nReceived messages:")
            for i, msg in enumerate(
                subscriber.received_messages[:10], 1
            ):  # Show first 10
                print(f"  {i}. Topic: {msg['topic']}")
                print(f"     Size: {msg['payload_size']} bytes")
                print(f"     Time: {time.ctime(msg['timestamp'])}")
            if len(subscriber.received_messages) > 10:
                print(
                    f"  ... and {len(subscriber.received_messages) - 10} more messages"
                )
    else:
        print("\n❌ WARNING: No messages received!")
        print("Possible issues:")
        print("  - Geo router service may not be running")
        print("  - Geohash calculation may not match subscription pattern")
        print("  - Check geo router logs for errors")

    # Load test summary
    if load_test:
        total_received = subscriber.message_count + subscriber.duplicate_count
        receive_duration = time.time() - wait_start
        print("\n" + "-" * 80)
        print("Load Test Summary")
        print("-" * 80)
        print(f"  Total messages published: {len(bsm_messages)}")
        print(f"  Publish rate:             {actual_rate:.2f} msg/s")
        print(f"  Total messages received:  {total_received}")
        print(
            f"  Receive rate:             {total_received / receive_duration:.2f} msg/s"
            if receive_duration > 0
            else "  Receive rate:             0.00 msg/s"
        )
        print(f"  Unique topic+payload:     {subscriber.message_count}")
        if publisher.published_count > 0:
            # Calculate average messages received per published message
            avg_messages = total_received / publisher.published_count
            print(f"  Avg messages per publish:  {avg_messages:.1f}")

    # Cleanup
    print("\n[Cleanup] Disconnecting...")
    subscriber.disconnect()
    publisher.disconnect()
    time.sleep(1)

    print("\n" + "=" * 80)
    print("Test completed!")
    print("=" * 80)


def main():
    """Main function."""
    parser = argparse.ArgumentParser(
        description="Test V2X Geo Router System",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Test with default coordinates (Atlanta area)
  python scripts/geo_router/test_geo_router.py

  # Test with custom coordinates
  python scripts/geo_router/test_geo_router.py --lat 37.7749 --lon -122.4194

  # Test with multiple messages
  python scripts/geo_router/test_geo_router.py --num-messages 5

  # Load test: 100 messages at 50 msg/s
  python scripts/geo_router/test_geo_router.py --load-test --num-messages 100 --rate 50

  # Load test: 1000 messages at 100 msg/s (quiet mode)
  python scripts/geo_router/test_geo_router.py --load-test --num-messages 1000 --rate 100 --quiet

  # Test with custom broker
  MQTT_BROKER_HOST=192.168.1.100 python scripts/geo_router/test_geo_router.py
        """,
    )
    parser.add_argument(
        "--lat",
        type=float,
        default=34.0554976,
        help="Latitude for BSM messages (default: 34.0554976)",
    )
    parser.add_argument(
        "--lon",
        type=float,
        default=-84.2760438,
        help="Longitude for BSM messages (default: -84.2760438)",
    )
    parser.add_argument(
        "--num-messages",
        type=int,
        default=1,
        help="Number of BSM messages to publish (default: 1)",
    )
    parser.add_argument(
        "--delay",
        type=float,
        default=3.0,
        help="Delay in seconds to wait for routing (default: 3.0)",
    )
    parser.add_argument(
        "--load-test",
        action="store_true",
        help="Run in load test mode (suppress per-message output)",
    )
    parser.add_argument(
        "--rate",
        type=float,
        default=10.0,
        help="Messages per second for load testing (default: 10.0)",
    )
    parser.add_argument(
        "--quiet",
        action="store_true",
        help="Suppress detailed per-message output",
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

    args = parser.parse_args()

    # Override environment variables if provided
    global MQTT_BROKER_HOST, MQTT_BROKER_PORT, SERIALIZER_ENDPOINT
    if args.broker_host:
        MQTT_BROKER_HOST = args.broker_host
    if args.broker_port:
        MQTT_BROKER_PORT = args.broker_port
    if args.serializer_url:
        SERIALIZER_ENDPOINT = f"{args.serializer_url}/jer/uper/hex"

    try:
        test_geo_routing(
            latitude=args.lat,
            longitude=args.lon,
            num_messages=args.num_messages,
            delay=args.delay,
            load_test=args.load_test,
            messages_per_second=args.rate,
            quiet=args.quiet,
        )
    except KeyboardInterrupt:
        print("\n\n[Interrupted] Test cancelled by user")
        sys.exit(1)
    except Exception as e:
        print(f"\n\n[Error] Test failed: {e}")
        import traceback

        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
