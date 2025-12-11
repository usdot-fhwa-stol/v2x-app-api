package usdot.v2x.hivemq.routing.service;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Location data extracted from V2X messages
 */
@Data
@AllArgsConstructor
public class LocationData {
    private double latitude;
    private double longitude;
}
