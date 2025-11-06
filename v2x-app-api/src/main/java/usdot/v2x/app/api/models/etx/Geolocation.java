package usdot.v2x.app.api.models.etx;

import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.registration.ConnectionPostRequest;
import lombok.Data;

@Data
public class Geolocation {
    @JsonProperty("Latitude")
    private double latitude;
    @JsonProperty("Longitude")
    private double longitude;

    public Geolocation(ConnectionPostRequest request) {
        this.latitude = request.getLat();
        this.longitude = request.getLon();
    }
}
