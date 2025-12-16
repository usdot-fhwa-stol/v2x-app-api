package usdot.v2x.app.api.models.etx.registration;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RegistrationClientType {
    @JsonProperty("Vehicle")
    Vehicle,
    @JsonProperty("VulnerableRoadUser")
    VulnerableRoadUser,
    @JsonProperty("TrafficLightController")
    TrafficLightController,
    @JsonProperty("InfrastructureSensor")
    InfrastructureSensor,
    @JsonProperty("OnboardSensor")
    OnboardSensor,
    @JsonProperty("Software")
    Software,
}
