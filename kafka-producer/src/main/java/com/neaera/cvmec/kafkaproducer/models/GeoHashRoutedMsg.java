package com.neaera.cvmec.kafkaproducer.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GeoHashRoutedMsg {
    private static final int LOG_HEX_PREVIEW_LENGTH = 128;

    private byte[] msgBytes;
    private String geohash;

    /**
     * Constructor to create from hex payload string
     */
    public GeoHashRoutedMsg(String hexPayload, String geohash) {
        this.msgBytes = hexToBytes(hexPayload);
        this.geohash = geohash;
    }

    /**
     * Set the message bytes from hex string
     */
    public void setMsgBytes(String hexPayload) {
        this.msgBytes = hexToBytes(hexPayload);
    }

    /**
     * Convert hex string to byte array
     */
    private byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }

        // Remove any spaces or special characters
        hex = hex.replaceAll("[^0-9A-Fa-f]", "");

        if (hex.length() % 2 != 0) {
            hex = "0" + hex; // Pad with leading zero if odd length
        }

        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return bytes;
    }

    /**
     * Compact description for logging: geohash, payload size, and a truncated hex preview.
     */
    public String toLogDescription() {
        int payloadBytes = msgBytes != null ? msgBytes.length : 0;
        return "geohash=" + geohash + ", payloadBytes=" + payloadBytes + ", payloadHex="
                + formatHexPreview(msgBytes);
    }

    static String formatHexPreview(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        StringBuilder hex = new StringBuilder(Math.min(bytes.length * 2, LOG_HEX_PREVIEW_LENGTH + 3));
        int maxBytes = LOG_HEX_PREVIEW_LENGTH / 2;
        for (int i = 0; i < bytes.length && i < maxBytes; i++) {
            hex.append(String.format("%02x", bytes[i]));
        }
        if (bytes.length > maxBytes) {
            hex.append("...");
        }
        return hex.toString();
    }
}
