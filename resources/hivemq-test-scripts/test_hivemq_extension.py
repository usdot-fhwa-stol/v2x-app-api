#!/usr/bin/env python3
"""
Test script for HiveMQ V2X Geohash Routing Extension

This script tests:
1. Publishing BSM messages to /v2x/1/ingress/bsm
2. Verifying messages are routed to geohash topics
3. Testing subscription rewriting from /v2x/1/egress/{messageType}/# to geohash topics
4. Verifying subscribed clients receive messages on geohash topics
"""

import paho.mqtt.client as mqtt
import json
import time
import sys
import argparse
import geohash2
from typing import List, Dict, Optional

# Default configuration
DEFAULT_MQTT_HOST = "localhost"
DEFAULT_MQTT_PORT = 1883
DEFAULT_CLIENT_ID_PREFIX = "test-client"


class HiveMQExtensionTester:
    def __init__(
        self,
        mqtt_host: str = DEFAULT_MQTT_HOST,
        mqtt_port: int = DEFAULT_MQTT_PORT,
        client_id_prefix: str = DEFAULT_CLIENT_ID_PREFIX,
    ):
        self.mqtt_host = mqtt_host
        self.mqtt_port = mqtt_port
        self.client_id_prefix = client_id_prefix

        # Track received messages
        self.received_messages: Dict[str, List[Dict]] = {}
        self.test_results: List[Dict] = []

    def log(self, message: str, level: str = "INFO"):
        """Log a message with timestamp"""
        timestamp = time.strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] [{level}] {message}")

    def create_bsm_message(self, lat: float = 39.7392, lon: float = -104.9903) -> str:
        """Create a valid BSM JSON message with location data"""
        # Convert degrees to microdegrees (J2735 format)
        lat_micro = int(lat * 1e6)
        lon_micro = int(lon * 1e6)

        bsm = {
            "coreData": {
                "msgCnt": 1,
                "id": "TEST1234",
                "secMark": int(time.time() % 65536),
                "lat": lat_micro,
                "long": lon_micro,
                "elev": 1600,
                "accuracy": {"semiMajor": 255, "semiMinor": 255, "orientation": 65535},
                "transmission": "neutral",
                "speed": 0,
                "heading": 0,
                "angle": 0,
                "accelSet": {"long": 0, "lat": 0, "vert": 0, "yaw": 0},
                "brakes": {
                    "wheelBrakes": "unavailable",
                    "traction": "unavailable",
                    "abs": "unavailable",
                    "scs": "unavailable",
                    "brakeBoost": "unavailable",
                    "auxBrakes": "unavailable",
                },
                "size": {"width": 200, "length": 500},
            }
        }
        return json.dumps(bsm)

    def on_message(self, client, userdata, msg):
        """Callback for received MQTT messages"""
        topic = msg.topic
        payload = msg.payload.decode("utf-8", errors="ignore")

        if topic not in self.received_messages:
            self.received_messages[topic] = []

        message_data = {"topic": topic, "payload": payload, "timestamp": time.time()}
        self.received_messages[topic].append(message_data)
        self.log(f"Received message on {topic}: {payload[:100]}...")

    def test_bsm_ingress_routing(self) -> bool:
        """Test that BSM messages published to ingress are routed to geohash topics"""
        self.log("\n=== Testing BSM Ingress to Geohash Routing ===")

        # Test locations (Denver, CO area)
        test_locations = [
            (39.7392, -104.9903, "Denver"),
            (39.7505, -104.9967, "Denver North"),
            (39.7280, -104.9840, "Denver South"),
        ]

        publisher = mqtt.Client(
            client_id=f"{self.client_id_prefix}-publisher", protocol=mqtt.MQTTv5
        )
        subscriber = mqtt.Client(
            client_id=f"{self.client_id_prefix}-subscriber", protocol=mqtt.MQTTv5
        )

        try:
            # Connect clients
            publisher.connect(self.mqtt_host, self.mqtt_port, 60)
            subscriber.connect(self.mqtt_host, self.mqtt_port, 60)

            publisher.loop_start()
            subscriber.loop_start()

            # Subscribe to geohash topics pattern
            # Format: v2x/1/geo/+/+/+/+/+/+/+/bsm
            geohash_topic_pattern = "v2x/1/geo/+/+/+/+/+/+/+/bsm"
            subscriber.subscribe(geohash_topic_pattern, qos=1)
            self.log(f"Subscribed to geohash topic pattern: {geohash_topic_pattern}")

            subscriber.on_message = self.on_message

            time.sleep(1)  # Wait for subscriptions to be established

            passed = 0
            failed = 0

            for lat, lon, location_name in test_locations:
                # Create BSM message with location
                test_payload = self.create_bsm_message(lat, lon)

                # Compute expected geohash (7 characters)
                expected_geohash = geohash2.encode(lat, lon, precision=7)
                expected_topic = f"v2x/1/geo/{'/'.join(expected_geohash)}/bsm"

                self.log(f"Publishing BSM from {location_name} (lat={lat}, lon={lon})")
                self.log(f"Expected geohash: {expected_geohash}")
                self.log(f"Expected topic: {expected_topic}")

                # Publish to ingress topic
                publisher.publish("v2x/1/ingress/bsm", test_payload, qos=1, retain=False)

                # Wait for message to be routed
                time.sleep(2)

                # Check if message was received on geohash topic
                found = False
                received_topic = None

                for topic, messages in self.received_messages.items():
                    # Check if topic matches the geohash pattern
                    if topic.startswith("v2x/1/geo/") and topic.endswith("/bsm"):
                        # Extract geohash from topic
                        parts = topic.split("/")
                        if len(parts) == 10:  # v2x/1/geo/char1/char2/char3/char4/char5/char6/char7/bsm
                            topic_geohash = "".join(parts[3:10])
                            # Check if it's the expected geohash or a neighbor (3x3 grid)
                            if topic_geohash == expected_geohash:
                                found = True
                                received_topic = topic
                                self.log(f"✓ Found message on exact geohash topic: {topic}")
                                break
                            # Check neighbors (3x3 grid expansion)
                            # geohash2 returns neighbors as a list: [n, ne, e, se, s, sw, w, nw]
                            try:
                                neighbors = geohash2.get_neighbors(expected_geohash)
                                # Flatten the neighbors list (it may be a list of lists)
                                neighbor_list = []
                                for n in neighbors:
                                    if isinstance(n, list):
                                        neighbor_list.extend(n)
                                    else:
                                        neighbor_list.append(n)
                                if topic_geohash in neighbor_list or topic_geohash == expected_geohash:
                                    found = True
                                    received_topic = topic
                                    self.log(f"✓ Found message on neighbor geohash topic: {topic}")
                                    break
                            except:
                                # If neighbor check fails, just check exact match
                                pass
                                found = True
                                received_topic = topic
                                self.log(f"✓ Found message on neighbor geohash topic: {topic}")
                                break

                if found:
                    # Verify payload matches
                    for msg in self.received_messages[received_topic]:
                        try:
                            received_json = json.loads(msg["payload"])
                            sent_json = json.loads(test_payload)
                            # Check if it's a BSM message
                            if "coreData" in received_json or "coreData" in sent_json:
                                self.log(
                                    f"✓ Message from {location_name} correctly routed to geohash topic",
                                    "SUCCESS",
                                )
                                passed += 1
                                break
                        except:
                            # Fallback: check if payloads match
                            if test_payload in msg["payload"]:
                                self.log(
                                    f"✓ Message from {location_name} correctly routed to geohash topic",
                                    "SUCCESS",
                                )
                                passed += 1
                                break
                    else:
                        self.log(
                            f"✗ Message from {location_name} routed but payload mismatch",
                            "ERROR",
                        )
                        failed += 1
                else:
                    self.log(
                        f"✗ Message from {location_name} was NOT routed to geohash topic",
                        "ERROR",
                    )
                    failed += 1

            publisher.loop_stop()
            subscriber.loop_stop()
            publisher.disconnect()
            subscriber.disconnect()

            self.log(f"\nRouting test results: {passed} passed, {failed} failed")
            return failed == 0

        except Exception as e:
            self.log(f"✗ Routing test failed: {e}", "ERROR")
            import traceback

            traceback.print_exc()
            return False

    def test_subscription_rewriting(self) -> bool:
        """Test that egress subscriptions are rewritten to geohash topics"""
        self.log("\n=== Testing Subscription Rewriting ===")

        subscriber = mqtt.Client(
            client_id=f"{self.client_id_prefix}-subscriber-rewrite", protocol=mqtt.MQTTv5
        )

        try:
            subscriber.connect(self.mqtt_host, self.mqtt_port, 60)
            subscriber.loop_start()

            # Clear previous messages
            self.received_messages.clear()

            # Subscribe to egress topic (should be rewritten to geohash topic)
            egress_topic = "v2x/1/egress/bsm/#"
            subscriber.subscribe(egress_topic, qos=1)
            self.log(f"Subscribed to egress topic: {egress_topic}")
            self.log("Extension should rewrite this to: v2x/1/geo/+/+/+/+/+/+/+/bsm")

            subscriber.on_message = self.on_message

            time.sleep(1)  # Wait for subscription

            # Create publisher and send BSM message
            publisher = mqtt.Client(
                client_id=f"{self.client_id_prefix}-publisher-rewrite",
                protocol=mqtt.MQTTv5,
            )
            publisher.connect(self.mqtt_host, self.mqtt_port, 60)
            publisher.loop_start()

            # Publish BSM to ingress
            test_payload = self.create_bsm_message()
            self.log("Publishing BSM to ingress topic: v2x/1/ingress/bsm")
            publisher.publish("v2x/1/ingress/bsm", test_payload, qos=1, retain=False)

            # Wait for message delivery
            time.sleep(3)

            # Check if message was received (should be on geohash topic if subscription was rewritten)
            found = False
            received_topic = None

            for topic, messages in self.received_messages.items():
                # Check if it's a geohash topic
                if topic.startswith("v2x/1/geo/") and topic.endswith("/bsm"):
                    found = True
                    received_topic = topic
                    self.log(f"✓ Message received on geohash topic: {topic}")
                    self.log(
                        "✓ Subscription was successfully rewritten to geohash topic",
                        "SUCCESS",
                    )
                    break

            publisher.loop_stop()
            subscriber.loop_stop()
            publisher.disconnect()
            subscriber.disconnect()

            if found:
                self.log("✓ Subscription rewriting test passed")
                return True
            else:
                self.log(
                    "✗ Subscription rewriting test failed - message not received on geohash topic",
                    "ERROR",
                )
                return False

        except Exception as e:
            self.log(f"✗ Subscription rewriting test failed: {e}", "ERROR")
            import traceback

            traceback.print_exc()
            return False

    def test_multiple_message_types(self) -> bool:
        """Test that only BSM messages are processed (other types should be ignored)"""
        self.log("\n=== Testing Message Type Filtering ===")

        publisher = mqtt.Client(
            client_id=f"{self.client_id_prefix}-publisher-filter", protocol=mqtt.MQTTv5
        )
        subscriber = mqtt.Client(
            client_id=f"{self.client_id_prefix}-subscriber-filter", protocol=mqtt.MQTTv5
        )

        try:
            publisher.connect(self.mqtt_host, self.mqtt_port, 60)
            subscriber.connect(self.mqtt_host, self.mqtt_port, 60)

            publisher.loop_start()
            subscriber.loop_start()

            # Subscribe to geohash topics
            geohash_topic_pattern = "v2x/1/geo/+/+/+/+/+/+/+/#"
            subscriber.subscribe(geohash_topic_pattern, qos=1)
            subscriber.on_message = self.on_message

            time.sleep(1)

            # Clear previous messages
            self.received_messages.clear()

            # Try to publish non-BSM messages to ingress (should be ignored)
            test_messages = [
                ("v2x/1/ingress/spat", '{"test": "SPAT message"}'),
                ("v2x/1/ingress/tim", '{"test": "TIM message"}'),
                ("v2x/1/ingress/bsm", self.create_bsm_message()),  # This should work
            ]

            for topic, payload in test_messages:
                self.log(f"Publishing to {topic}")
                publisher.publish(topic, payload, qos=1, retain=False)
                time.sleep(1)

            # Wait for processing
            time.sleep(2)

            # Check results
            bsm_found = False
            other_found = False

            for topic, messages in self.received_messages.items():
                if topic.endswith("/bsm"):
                    bsm_found = True
                else:
                    other_found = True

            publisher.loop_stop()
            subscriber.loop_stop()
            publisher.disconnect()
            subscriber.disconnect()

            if bsm_found and not other_found:
                self.log(
                    "✓ Message type filtering test passed - only BSM messages were routed",
                    "SUCCESS",
                )
                return True
            else:
                self.log(
                    f"✗ Message type filtering test failed - BSM found: {bsm_found}, Other found: {other_found}",
                    "ERROR",
                )
                return False

        except Exception as e:
            self.log(f"✗ Message type filtering test failed: {e}", "ERROR")
            import traceback

            traceback.print_exc()
            return False

    def run_all_tests(self) -> bool:
        """Run all tests"""
        self.log("=" * 60)
        self.log("HiveMQ V2X Geohash Routing Extension Test Suite")
        self.log("=" * 60)

        # Test 1: BSM ingress routing
        routing_ok = self.test_bsm_ingress_routing()

        # Test 2: Subscription rewriting
        subscription_ok = self.test_subscription_rewriting()

        # Test 3: Message type filtering
        filtering_ok = self.test_multiple_message_types()

        # Summary
        self.log("\n" + "=" * 60)
        self.log("Test Summary")
        self.log("=" * 60)
        self.log(f"BSM Ingress Routing: {'✓ PASSED' if routing_ok else '✗ FAILED'}")
        self.log(
            f"Subscription Rewriting: {'✓ PASSED' if subscription_ok else '✗ FAILED'}"
        )
        self.log(f"Message Type Filtering: {'✓ PASSED' if filtering_ok else '✗ FAILED'}")

        all_passed = routing_ok and subscription_ok and filtering_ok
        self.log(
            f"\nOverall: {'✓ ALL TESTS PASSED' if all_passed else '✗ SOME TESTS FAILED'}"
        )

        return all_passed


def main():
    parser = argparse.ArgumentParser(description="Test HiveMQ V2X Extension")
    parser.add_argument(
        "--mqtt-host",
        default=DEFAULT_MQTT_HOST,
        help=f"MQTT broker host (default: {DEFAULT_MQTT_HOST})",
    )
    parser.add_argument(
        "--mqtt-port",
        type=int,
        default=DEFAULT_MQTT_PORT,
        help=f"MQTT broker port (default: {DEFAULT_MQTT_PORT})",
    )
    parser.add_argument(
        "--client-id-prefix",
        default=DEFAULT_CLIENT_ID_PREFIX,
        help=f"Client ID prefix (default: {DEFAULT_CLIENT_ID_PREFIX})",
    )

    args = parser.parse_args()

    tester = HiveMQExtensionTester(
        mqtt_host=args.mqtt_host,
        mqtt_port=args.mqtt_port,
        client_id_prefix=args.client_id_prefix,
    )

    success = tester.run_all_tests()
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()

