package usdot.v2x.mqtt.router.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import usdot.v2x.mqtt.router.service.GeorelevanceRouterService;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for managing client subscriptions to georelevance topics
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {
    private final GeorelevanceRouterService routerService;

    public SubscriptionController(GeorelevanceRouterService routerService) {
        this.routerService = routerService;
    }

    @PostMapping("/{clientId}")
    public ResponseEntity<Map<String, Object>> registerSubscription(
            @PathVariable("clientId") String clientId,
            @RequestBody Map<String, String> request) {
        String topicPattern = request.get("topicPattern");
        if (topicPattern == null || topicPattern.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "topicPattern is required");
            return ResponseEntity.badRequest().body(error);
        }

        routerService.registerClientSubscription(clientId, topicPattern);

        Map<String, Object> response = new HashMap<>();
        response.put("clientId", clientId);
        response.put("topicPattern", topicPattern);
        response.put("status", "registered");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{clientId}")
    public ResponseEntity<Map<String, Object>> unregisterSubscription(
            @PathVariable("clientId") String clientId,
            @RequestParam(value = "topicPattern", required = false) String topicPattern) {

        if (topicPattern != null && !topicPattern.isEmpty()) {
            routerService.unregisterClientSubscription(clientId, topicPattern);
        } else {
            routerService.unregisterClient(clientId);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("clientId", clientId);
        response.put("status", "unregistered");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{clientId}")
    public ResponseEntity<Map<String, Object>> getSubscriptions(@PathVariable("clientId") String clientId) {
        Set<String> subscriptions = routerService.getClientSubscriptions(clientId);

        Map<String, Object> response = new HashMap<>();
        response.put("clientId", clientId);
        response.put("subscriptions", subscriptions);
        return ResponseEntity.ok(response);
    }
}
