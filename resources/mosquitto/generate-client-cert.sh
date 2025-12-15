#!/bin/bash
# Script to generate client certificate for a specific client ID

set -e

if [ -z "$1" ]; then
    echo "Usage: $0 <client_id>"
    exit 1
fi

CLIENT_ID="$1"
# Use CERT_DIR environment variable if provided, otherwise use script's certs directory
if [ -n "$CERT_DIR" ]; then
    CERT_DIR="$CERT_DIR"
else
    CERT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/certs"
fi
CA_KEY="$CERT_DIR/ca.key"
CA_CERT="$CERT_DIR/ca.crt"
CLIENT_KEY="$CERT_DIR/${CLIENT_ID}.key"
CLIENT_CERT="$CERT_DIR/${CLIENT_ID}.crt"
CLIENT_DAYS="${CLIENT_CERT_DAYS:-365}"

if [ ! -f "$CA_KEY" ] || [ ! -f "$CA_CERT" ]; then
    echo "Error: CA certificate not found. Please run generate-certs.sh first."
    exit 1
fi

echo "Generating client key for $CLIENT_ID..."
openssl genrsa -out "$CLIENT_KEY" 2048

echo "Generating client certificate signing request..."
openssl req -new -key "$CLIENT_KEY" -out "$CERT_DIR/${CLIENT_ID}.csr" \
    -subj "/CN=$CLIENT_ID/O=V2X-App-API/C=US"

echo "Signing client certificate with CA..."
openssl x509 -req -in "$CERT_DIR/${CLIENT_ID}.csr" -CA "$CA_CERT" -CAkey "$CA_KEY" \
    -CAcreateserial -out "$CLIENT_CERT" -days "$CLIENT_DAYS" \
    -extensions v3_req -extfile <(
        echo "[v3_req]"
        echo "keyUsage = digitalSignature, keyEncipherment"
        echo "extendedKeyUsage = clientAuth"
    )

# Clean up CSR
rm -f "$CERT_DIR/${CLIENT_ID}.csr"

echo "Setting permissions..."
chmod 600 "$CLIENT_KEY"
chmod 644 "$CLIENT_CERT"

echo "Client certificate generation complete!"
echo "Client Key: $CLIENT_KEY"
echo "Client Certificate: $CLIENT_CERT"
echo "CA Certificate: $CA_CERT"

