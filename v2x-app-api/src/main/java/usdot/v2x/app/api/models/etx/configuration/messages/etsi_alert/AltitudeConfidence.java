package usdot.v2x.app.api.models.etx.configuration.messages.etsi_alert;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Enum representing the confidence levels for altitude in the ETX configuration
 * messages.
 * Each enum constant corresponds to a specific altitude confidence level.
 */
public enum AltitudeConfidence {
    @JsonProperty("alt-000-01")
    ALT_000_01,
    @JsonProperty("alt-000-02")
    ALT_000_02,
    @JsonProperty("alt-000-05")
    ALT_000_05,
    @JsonProperty("alt-000-10")
    ALT_000_10,
    @JsonProperty("alt-000-20")
    ALT_000_20,
    @JsonProperty("alt-000-50")
    ALT_000_50,
    @JsonProperty("alt-001-00")
    ALT_001_00,
    @JsonProperty("alt-002-00")
    ALT_002_00,
    @JsonProperty("alt-005-00")
    ALT_005_00,
    @JsonProperty("alt-010-00")
    ALT_010_00,
    @JsonProperty("alt-020-00")
    ALT_020_00,
    @JsonProperty("alt-050-00")
    ALT_050_00,
    @JsonProperty("alt-100-00")
    ALT_100_00,
    @JsonProperty("alt-200-00")
    ALT_200_00,
    @JsonProperty("outOfRange")
    OUT_OF_RANGE,
    @JsonProperty("unavailable")
    UNAVAILABLE,
}