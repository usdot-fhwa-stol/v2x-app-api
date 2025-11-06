package usdot.v2x.app.api.models.etx.registration;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RegistrationClientSubType {
    @JsonProperty("PassengerCar")
    PassengerCar,
    @JsonProperty("Truck")
    Truck,
    @JsonProperty("Bus")
    Bus,
    @JsonProperty("EmergencyVehicle")
    EmergencyVehicle,
    @JsonProperty("SchoolBus")
    SchoolBus,
    @JsonProperty("MaintenanceVehicle")
    MaintenanceVehicle,
    @JsonProperty("Pedestrian")
    Pedestrian,
    @JsonProperty("Bicycle")
    Bicycle,
    @JsonProperty("Scooter")
    Scooter,
    @JsonProperty("Motorcycle")
    Motorcycle,
    @JsonProperty("RoadSideUnit")
    RoadSideUnit,
    @JsonProperty("Camera")
    Camera,
    @JsonProperty("Lidar")
    Lidar,
    @JsonProperty("Radar")
    Radar,
    @JsonProperty("InductiveLoop")
    InductiveLoop,
    @JsonProperty("MagneticSensor")
    MagneticSensor,
    @JsonProperty("Platform")
    Platform,
    @JsonProperty("Application")
    Application,
    @JsonProperty("NA")
    NA,
}
