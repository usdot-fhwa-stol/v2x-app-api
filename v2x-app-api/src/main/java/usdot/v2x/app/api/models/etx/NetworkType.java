package usdot.v2x.app.api.models.etx;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum NetworkType {
    @JsonProperty("non-VZ")
    NON_VZ,
    @JsonProperty("VZ")
    VZ
}
