package usdot.v2x.mqtt.router.service;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.stereotype.Service;
import usdot.v2x.mqtt.router.config.MqttProperties;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Slf4j
@Service
public class MqttClientService {
    private final MqttProperties mqttProperties;
    private MqttAsyncClient mqttClient;
    private boolean connected = false;
    private java.util.function.BiConsumer<String, MqttMessage> messageHandler;

    public MqttClientService(MqttProperties mqttProperties) {
        this.mqttProperties = mqttProperties;
    }

    @PostConstruct
    public void init() {
        try {
            String clientId = "v2x-mqtt-router-" + System.currentTimeMillis();
            mqttClient = new MqttAsyncClient(
                    mqttProperties.getBroker().getConnectionUrl(),
                    clientId,
                    new MemoryPersistence());

            // Set callback to handle incoming messages
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void disconnected(MqttDisconnectResponse disconnectResponse) {
                    log.warn("MQTT connection lost: {}",
                            disconnectResponse != null ? disconnectResponse.getReasonString() : "Unknown reason");
                    connected = false;
                }

                @Override
                public void mqttErrorOccurred(MqttException exception) {
                    log.error("MQTT error occurred: {}", exception.getMessage(), exception);
                    connected = false;
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    try {
                        if (messageHandler != null) {
                            messageHandler.accept(topic, message);
                        }
                    } catch (Exception e) {
                        log.error("Error in message handler for topic {}: {}", topic, e.getMessage(), e);
                    }
                }

                @Override
                public void deliveryComplete(IMqttToken token) {
                    // Optional: log successful delivery
                }

                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    log.info("MQTT connection complete. Reconnect: {}", reconnect);
                    connected = true;
                }

                @Override
                public void authPacketArrived(int reasonCode,
                        org.eclipse.paho.mqttv5.common.packet.MqttProperties properties) {
                    log.debug("Auth packet arrived: reasonCode={}", reasonCode);
                }
            });

            connect();
        } catch (MqttException e) {
            log.error("Failed to initialize MQTT client: {}", e.getMessage(), e);
        }
    }

    private void connect() {
        if (mqttClient == null || mqttClient.isConnected()) {
            return;
        }

        try {
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setCleanStart(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(30);
            options.setKeepAliveInterval(60);

            // Set MQTT version to 5.0 (default is 5.0 for MqttConnectionOptions, but
            // explicitly set it)
            // MQTT v5 is the default for MqttConnectionOptions, so no need to set
            // explicitly

            if (mqttProperties.getBroker().getUsername() != null &&
                    !mqttProperties.getBroker().getUsername().isEmpty()) {
                options.setUserName(mqttProperties.getBroker().getUsername());
                if (mqttProperties.getBroker().getPassword() != null) {
                    options.setPassword(mqttProperties.getBroker().getPassword().getBytes());
                }
            }

            mqttClient.connect(options).waitForCompletion();
            connected = true;
            log.info("Connected to MQTT broker at {} (MQTT v5.0)", mqttProperties.getBroker().getConnectionUrl());
        } catch (MqttException e) {
            log.error("Failed to connect to MQTT broker: {}", e.getMessage(), e);
            connected = false;
        }
    }

    public void publish(String topic, byte[] payload) {
        if (!connected || mqttClient == null || !mqttClient.isConnected()) {
            connect();
            if (!connected) {
                log.warn("Cannot publish message: MQTT client not connected");
                return;
            }
        }

        try {
            MqttMessage message = new MqttMessage(payload);
            message.setQos(mqttProperties.getQos());
            message.setRetained(mqttProperties.isRetain());

            mqttClient.publish(topic, message).waitForCompletion();
            log.debug("Published message to topic: {}", topic);
        } catch (MqttException e) {
            log.error("Failed to publish message to topic {}: {}", topic, e.getMessage(), e);
            connected = false;
        }
    }

    public void deleteTopic(String topic) {
        if (!connected || mqttClient == null || !mqttClient.isConnected()) {
            connect();
            if (!connected) {
                log.warn("Cannot delete topic: MQTT client not connected");
                return;
            }
        }

        try {
            // Publish empty retained message to clear retained messages
            MqttMessage message = new MqttMessage(new byte[0]);
            message.setQos(mqttProperties.getQos());
            message.setRetained(true);

            mqttClient.publish(topic, message).waitForCompletion();
            log.debug("Cleared retained message for topic: {}", topic);
        } catch (MqttException e) {
            log.error("Failed to clear topic {}: {}", topic, e.getMessage(), e);
            connected = false;
        }
    }

    /**
     * Subscribe to a topic pattern with a message handler
     */
    public void subscribe(String topicFilter, java.util.function.BiConsumer<String, MqttMessage> messageHandler) {
        this.messageHandler = messageHandler;

        if (!connected || mqttClient == null || !mqttClient.isConnected()) {
            connect();
            if (!connected) {
                log.warn("Cannot subscribe: MQTT client not connected");
                return;
            }
        }

        try {
            mqttClient.subscribe(topicFilter, mqttProperties.getQos()).waitForCompletion();
            log.info("Subscribed to topic: {} (MQTT v5.0)", topicFilter);
        } catch (MqttException e) {
            log.error("Failed to subscribe to topic {}: {}", topicFilter, e.getMessage(), e);
            connected = false;
        }
    }

    public boolean isConnected() {
        return connected && mqttClient != null && mqttClient.isConnected();
    }

    @PreDestroy
    public void disconnect() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect().waitForCompletion();
                mqttClient.close();
                log.info("Disconnected from MQTT broker");
            } catch (MqttException e) {
                log.error("Error disconnecting from MQTT broker: {}", e.getMessage(), e);
            }
        }
    }
}
