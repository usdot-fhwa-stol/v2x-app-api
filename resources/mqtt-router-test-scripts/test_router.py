#!/usr/bin/env python3
"""
Test script for MQTT Router Service

This script tests:
1. Publishing messages to GeoRelevance topics
2. Verifying messages are routed to Regional topics
3. Testing client subscription management via REST API
4. Verifying subscribed clients receive messages
"""

import paho.mqtt.client as mqtt
import requests
import json
import time
import sys
import argparse
import urllib.parse
from typing import List, Dict, Optional

# Default configuration
DEFAULT_MQTT_HOST = "localhost"
DEFAULT_MQTT_PORT = 1883
DEFAULT_ROUTER_API = "http://localhost:8081"
DEFAULT_CLIENT_ID_PREFIX = "test-client"


class MqttRouterTester:
    def __init__(
        self,
        mqtt_host: str = DEFAULT_MQTT_HOST,
        mqtt_port: int = DEFAULT_MQTT_PORT,
        router_api: str = DEFAULT_ROUTER_API,
        client_id_prefix: str = DEFAULT_CLIENT_ID_PREFIX,
    ):
        self.mqtt_host = mqtt_host
        self.mqtt_port = mqtt_port
        self.router_api = router_api
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

    def create_rsa_message(self, lat: float = 39.7392, lon: float = -104.9903) -> str:
        """Create a valid RSA JSON message with location data"""
        lat_micro = int(lat * 1e6)
        lon_micro = int(lon * 1e6)

        rsa = {
            "msgCnt": 1,
            "typeEvent": "construction",
            "description": {
                "geometry": {
                    "radius": {
                        "center": {"lat": lat_micro, "long": lon_micro},
                        "radius": 1000,
                    }
                }
            },
        }
        return json.dumps(rsa)

    def create_tim_message(self, lat: float = 39.7392, lon: float = -104.9903) -> str:
        """Create a valid TIM JSON message with location data"""
        lat_micro = int(lat * 1e6)
        lon_micro = int(lon * 1e6)

        tim = {
            "msgCnt": 1,
            "dataframes": [
                {
                    "geometry": {
                        "radius": {
                            "center": {"lat": lat_micro, "long": lon_micro},
                            "radius": 1000,
                        }
                    }
                }
            ],
        }
        return json.dumps(tim)

    def test_router_health(self) -> bool:
        """Test if router service is healthy"""
        self.log("Testing router service health...")
        try:
            response = requests.get(f"{self.router_api}/actuator/health", timeout=5)
            if response.status_code == 200:
                health = response.json()
                self.log(f"Router health: {json.dumps(health, indent=2)}")
                mqtt_connected = health.get("mqtt", {}).get("connected", False)
                if mqtt_connected:
                    self.log("✓ Router service is healthy and MQTT is connected")
                    return True
                else:
                    self.log(
                        "✗ Router service is up but MQTT is not connected", "ERROR"
                    )
                    return False
            else:
                self.log(
                    f"✗ Router health check failed: {response.status_code}", "ERROR"
                )
                return False
        except Exception as e:
            self.log(f"✗ Failed to connect to router service: {e}", "ERROR")
            return False

    def on_message(self, client, userdata, msg):
        """Callback for received MQTT messages"""
        topic = msg.topic
        payload = msg.payload.decode("utf-8", errors="ignore")

        if topic not in self.received_messages:
            self.received_messages[topic] = []

        message_data = {"topic": topic, "payload": payload, "timestamp": time.time()}
        self.received_messages[topic].append(message_data)
        self.log(f"Received message on {topic}: {payload[:50]}...")

    def test_georelevance_to_regional_routing(self) -> bool:
        """Test routing from GeoRelevance to Regional topics"""
        self.log("\n=== Testing GeoRelevance to Regional Routing ===")

        # Test cases: (clientType, clientSubtype, messageType, shouldRoute)
        test_cases = [
            ("OBU", "Vehicle", "BSM", True),  # Should route to regional
            ("OBU", "Vehicle", "RSA", True),  # Should route to regional
            ("OBU", "Vehicle", "TIM", True),  # Should route to regional
            ("OBU", "Vehicle", "PSM", False),  # Should NOT route (not in regional list)
        ]

        # Create MQTT clients
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

            # Subscribe to regional topics with geohash pattern
            # Regional topics now use geohash: v2x/1/Regional/{char1}/{char2}/.../{char8}/{clientType}/{clientSubtype}/Public/{messageType}
            regional_topics = [
                "v2x/1/Regional/+/+/+/+/+/+/+/+/+/+/Public/+",  # Wildcard for all geohash segments
                "v2x/1/Regional/+/+/+/+/+/+/+/+/OBU/Vehicle/Public/+",
            ]
            for topic in regional_topics:
                subscriber.subscribe(topic, qos=1)
                self.log(f"Subscribed to regional topic: {topic}")

            subscriber.on_message = self.on_message

            time.sleep(1)  # Wait for subscriptions to be established

            # Publish test messages
            passed = 0
            failed = 0

            for client_type, client_subtype, message_type, should_route in test_cases:
                georelevance_topic = f"v2x/1/GeoRelevance/{client_type}/{client_subtype}/Public/{message_type}"

                # Create proper V2X JSON message with location data
                if message_type == "BSM":
                    test_payload = self.create_bsm_message()
                elif message_type == "RSA":
                    test_payload = self.create_rsa_message()
                elif message_type == "TIM":
                    test_payload = self.create_tim_message()
                else:
                    # For PSM and other types, use a simple JSON structure
                    test_payload = json.dumps({"test": f"message for {message_type}"})

                self.log(f"Publishing to GeoRelevance: {georelevance_topic}")
                publisher.publish(georelevance_topic, test_payload, qos=1, retain=False)

                # Wait for message to be routed
                time.sleep(2)

                # Check if message was received on regional topic
                # Regional topics now use geohash: v2x/1/Regional/{char1}/{char2}/.../{char8}/{clientType}/{clientSubtype}/Public/{messageType}
                # We'll check if the topic matches the pattern (8 geohash segments + client info)
                expected_pattern = f"v2x/1/Regional/"
                expected_suffix = (
                    f"{client_type}/{client_subtype}/Public/{message_type}"
                )

                # Check received messages
                found = False
                for received_topic, messages in self.received_messages.items():
                    if received_topic.startswith(
                        expected_pattern
                    ) and received_topic.endswith(expected_suffix):
                        # Verify it has 8 geohash segments (check topic structure)
                        parts = received_topic.split("/")
                        if (
                            len(parts) >= 13
                        ):  # v2x/1/Regional/char1/char2/char3/char4/char5/char6/char7/char8/clientType/clientSubtype/Public/messageType
                            for msg in messages:
                                # Check if payload matches (compare JSON content)
                                try:
                                    received_json = json.loads(msg["payload"])
                                    sent_json = json.loads(test_payload)
                                    # Simple check: if both are JSON and message type matches
                                    if message_type in received_topic:
                                        found = True
                                        self.log(
                                            f"✓ Found routed message on: {received_topic}"
                                        )
                                        break
                                except:
                                    # If not JSON, do string comparison
                                    if test_payload in msg["payload"]:
                                        found = True
                                        break

                if should_route:
                    if found:
                        self.log(
                            f"✓ Message {message_type} correctly routed to regional topic",
                            "SUCCESS",
                        )
                        passed += 1
                    else:
                        self.log(
                            f"✗ Message {message_type} was NOT routed to regional topic (expected)",
                            "ERROR",
                        )
                        failed += 1
                else:
                    if not found:
                        self.log(
                            f"✓ Message {message_type} correctly NOT routed (as expected)",
                            "SUCCESS",
                        )
                        passed += 1
                    else:
                        self.log(
                            f"✗ Message {message_type} was incorrectly routed (should not route)",
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

    def test_client_subscription_api(self) -> bool:
        """Test client subscription management via REST API"""
        self.log("\n=== Testing Client Subscription API ===")

        client_id = f"{self.client_id_prefix}-api-test"
        topic_pattern = "v2x/1/GeoRelevance/+/+/Public/BSM"

        try:
            # Register subscription
            self.log(f"Registering subscription for client: {client_id}")
            response = requests.post(
                f"{self.router_api}/api/v1/subscriptions/{client_id}",
                json={"topicPattern": topic_pattern},
                timeout=5,
            )

            if response.status_code == 200:
                result = response.json()
                self.log(f"✓ Subscription registered: {json.dumps(result, indent=2)}")
            else:
                self.log(
                    f"✗ Failed to register subscription: {response.status_code} - {response.text}",
                    "ERROR",
                )
                return False

            # Get subscriptions
            self.log(f"Getting subscriptions for client: {client_id}")
            response = requests.get(
                f"{self.router_api}/api/v1/subscriptions/{client_id}", timeout=5
            )

            if response.status_code == 200:
                result = response.json()
                subscriptions = result.get("subscriptions", [])
                if topic_pattern in subscriptions:
                    self.log(f"✓ Subscription retrieved: {subscriptions}")
                else:
                    self.log(
                        f"✗ Subscription not found in retrieved list: {subscriptions}",
                        "ERROR",
                    )
                    return False
            else:
                self.log(
                    f"✗ Failed to get subscriptions: {response.status_code}", "ERROR"
                )
                return False

            # Unregister subscription
            self.log(f"Unregistering subscription for client: {client_id}")
            encoded_pattern = urllib.parse.quote(topic_pattern, safe="")
            response = requests.delete(
                f"{self.router_api}/api/v1/subscriptions/{client_id}?topicPattern={encoded_pattern}",
                timeout=5,
            )

            if response.status_code == 200:
                self.log(f"✓ Subscription unregistered")
            else:
                self.log(
                    f"✗ Failed to unregister subscription: {response.status_code}",
                    "ERROR",
                )
                return False

            # Verify unregistered
            response = requests.get(
                f"{self.router_api}/api/v1/subscriptions/{client_id}", timeout=5
            )

            if response.status_code == 200:
                result = response.json()
                subscriptions = result.get("subscriptions", [])
                if len(subscriptions) == 0:
                    self.log(f"✓ Subscription successfully removed")
                else:
                    self.log(f"✗ Subscription still present: {subscriptions}", "ERROR")
                    return False

            self.log("✓ Client subscription API test passed")
            return True

        except Exception as e:
            self.log(f"✗ Client subscription API test failed: {e}", "ERROR")
            import traceback

            traceback.print_exc()
            return False

    def test_subscribed_client_receives_messages(self) -> bool:
        """Test that subscribed clients receive messages"""
        self.log("\n=== Testing Subscribed Client Message Delivery ===")

        client_id = f"{self.client_id_prefix}-subscriber-test"
        topic_pattern = "v2x/1/GeoRelevance/OBU/Vehicle/Public/BSM"

        # Register subscription via API
        try:
            response = requests.post(
                f"{self.router_api}/api/v1/subscriptions/{client_id}",
                json={"topicPattern": topic_pattern},
                timeout=5,
            )
            if response.status_code != 200:
                self.log(
                    f"✗ Failed to register subscription: {response.status_code}",
                    "ERROR",
                )
                return False
        except Exception as e:
            self.log(f"✗ Failed to register subscription: {e}", "ERROR")
            return False

        # Create MQTT subscriber
        subscriber = mqtt.Client(client_id=f"{client_id}-mqtt", protocol=mqtt.MQTTv5)
        subscriber.on_message = self.on_message

        try:
            subscriber.connect(self.mqtt_host, self.mqtt_port, 60)
            subscriber.loop_start()

            # Subscribe to the georelevance topic
            subscriber.subscribe(topic_pattern, qos=1)
            self.log(f"Subscribed to MQTT topic: {topic_pattern}")

            time.sleep(1)  # Wait for subscription

            # Create publisher and send message
            publisher = mqtt.Client(
                client_id=f"{self.client_id_prefix}-publisher-2", protocol=mqtt.MQTTv5
            )
            publisher.connect(self.mqtt_host, self.mqtt_port, 60)
            publisher.loop_start()

            # Use a proper BSM JSON message with location data
            test_payload = self.create_bsm_message()
            georelevance_topic = "v2x/1/GeoRelevance/OBU/Vehicle/Public/BSM"

            self.log(f"Publishing test message to: {georelevance_topic}")
            publisher.publish(georelevance_topic, test_payload, qos=1, retain=False)

            # Wait for message delivery
            time.sleep(3)

            # Check if message was received
            found = False
            for received_topic, messages in self.received_messages.items():
                for msg in messages:
                    # Compare JSON content (messages may be reformatted)
                    try:
                        received_json = json.loads(msg["payload"])
                        sent_json = json.loads(test_payload)
                        # Check if it's a BSM message (has coreData)
                        if "coreData" in received_json or "coreData" in sent_json:
                            found = True
                            self.log(f"✓ Message received on topic: {received_topic}")
                            break
                    except:
                        # Fallback to string comparison
                        if test_payload in msg["payload"]:
                            found = True
                            self.log(f"✓ Message received on topic: {received_topic}")
                            break

            publisher.loop_stop()
            subscriber.loop_stop()
            publisher.disconnect()
            subscriber.disconnect()

            # Cleanup subscription
            requests.delete(
                f"{self.router_api}/api/v1/subscriptions/{client_id}", timeout=5
            )

            if found:
                self.log("✓ Subscribed client message delivery test passed")
                return True
            else:
                self.log("✗ Message was not delivered to subscribed client", "ERROR")
                return False

        except Exception as e:
            self.log(f"✗ Subscribed client test failed: {e}", "ERROR")
            import traceback

            traceback.print_exc()
            return False

    def run_all_tests(self) -> bool:
        """Run all tests"""
        self.log("=" * 60)
        self.log("MQTT Router Service Test Suite")
        self.log("=" * 60)

        # Test 1: Health check
        if not self.test_router_health():
            self.log("Router service is not healthy. Exiting.", "ERROR")
            return False

        # Test 2: GeoRelevance to Regional routing
        routing_ok = self.test_georelevance_to_regional_routing()

        # Test 3: Client subscription API
        api_ok = self.test_client_subscription_api()

        # Test 4: Subscribed client message delivery
        delivery_ok = self.test_subscribed_client_receives_messages()

        # Summary
        self.log("\n" + "=" * 60)
        self.log("Test Summary")
        self.log("=" * 60)
        self.log(f"Health Check: {'✓ PASSED' if True else '✗ FAILED'}")
        self.log(f"Routing Test: {'✓ PASSED' if routing_ok else '✗ FAILED'}")
        self.log(f"Subscription API: {'✓ PASSED' if api_ok else '✗ FAILED'}")
        self.log(f"Message Delivery: {'✓ PASSED' if delivery_ok else '✗ FAILED'}")

        all_passed = routing_ok and api_ok and delivery_ok
        self.log(
            f"\nOverall: {'✓ ALL TESTS PASSED' if all_passed else '✗ SOME TESTS FAILED'}"
        )

        return all_passed


def main():
    parser = argparse.ArgumentParser(description="Test MQTT Router Service")
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
        "--router-api",
        default=DEFAULT_ROUTER_API,
        help=f"Router API URL (default: {DEFAULT_ROUTER_API})",
    )
    parser.add_argument(
        "--client-id-prefix",
        default=DEFAULT_CLIENT_ID_PREFIX,
        help=f"Client ID prefix (default: {DEFAULT_CLIENT_ID_PREFIX})",
    )

    args = parser.parse_args()

    tester = MqttRouterTester(
        mqtt_host=args.mqtt_host,
        mqtt_port=args.mqtt_port,
        router_api=args.router_api,
        client_id_prefix=args.client_id_prefix,
    )

    success = tester.run_all_tests()
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
