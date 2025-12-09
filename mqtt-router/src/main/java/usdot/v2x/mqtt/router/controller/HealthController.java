package usdot.v2x.mqtt.router.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import usdot.v2x.mqtt.router.service.MqttClientService;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/actuator/health")
public class HealthController {
    private final MqttClientService mqttClientService;

    public HealthController(MqttClientService mqttClientService) {
        this.mqttClientService = mqttClientService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("mqtt", Map.of("connected", mqttClientService.isConnected()));
        return ResponseEntity.ok(health);
    }
}
