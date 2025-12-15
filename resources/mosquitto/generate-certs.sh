#!/bin/bash
# Script to generate self-signed certificates for Mosquitto MQTT broker

set -e

CERT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/certs"
mkdir -p "$CERT_DIR"

# CA Configuration
CA_KEY="$CERT_DIR/ca.key"
CA_CERT="$CERT_DIR/ca.crt"
CA_DAYS=3650

# Server Configuration
SERVER_KEY="$CERT_DIR/server.key"
SERVER_CERT="$CERT_DIR/server.crt"
SERVER_DAYS=365
SERVER_CN="${MOSQUITTO_SERVER_CN:-mosquitto}"

# Client Configuration (template)
CLIENT_KEY="$CERT_DIR/client.key"
CLIENT_CERT="$CERT_DIR/client.crt"
CLIENT_DAYS=365

echo "Generating CA key and certificate..."
openssl genrsa -out "$CA_KEY" 4096
openssl req -new -x509 -days "$CA_DAYS" -key "$CA_KEY" -out "$CA_CERT" \
    -subj "/CN=MQTT-CA/O=V2X-App-API/C=US"

echo "Generating server key..."
openssl genrsa -out "$SERVER_KEY" 2048

echo "Generating server certificate signing request..."
openssl req -new -key "$SERVER_KEY" -out "$CERT_DIR/server.csr" \
    -subj "/CN=$SERVER_CN/O=V2X-App-API/C=US"

echo "Signing server certificate with CA..."
openssl x509 -req -in "$CERT_DIR/server.csr" -CA "$CA_CERT" -CAkey "$CA_KEY" \
    -CAcreateserial -out "$SERVER_CERT" -days "$SERVER_DAYS" \
    -extensions v3_req -extfile <(
        echo "[v3_req]"
        echo "keyUsage = keyEncipherment, dataEncipherment"
        echo "extendedKeyUsage = serverAuth"
        echo "subjectAltName = @alt_names"
        echo "[alt_names]"
        echo "DNS.1 = $SERVER_CN"
        echo "DNS.2 = localhost"
        echo "IP.1 = 127.0.0.1"
    )

# Clean up CSR
rm -f "$CERT_DIR/server.csr"

echo "Setting permissions..."
chmod 600 "$CA_KEY" "$SERVER_KEY"
chmod 644 "$CA_CERT" "$SERVER_CERT"

echo "Certificate generation complete!"
echo "CA Certificate: $CA_CERT"
echo "Server Certificate: $SERVER_CERT"
echo "Server Key: $SERVER_KEY"

