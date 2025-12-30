package usdot.v2x.georouter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main Spring Boot application for the V2X Geo Router Service.
 * 
 * This service receives BSM messages via MQTT, decodes them to extract
 * geographic coordinates, calculates relevant geohash cells, and routes
 * messages to subscribers based on geographic relevance.
 */
@SpringBootApplication
@EnableAsync
public class GeoRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeoRouterApplication.class, args);
    }
}

