package usdot.v2x.georouter.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.springframework.stereotype.Service;
import usdot.v2x.georouter.config.MqttConfig;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Service for managing MQTT v5 client connection to a single broker.
 * Handles both subscribing to incoming BSM messages and publishing routed
 * messages.
 */
@Slf4j
@Service
public class MqttClientService {

    private final MqttConfig config;
    private MqttAsyncClient mqttClient;

    public MqttClientService(MqttConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void initialize() {
        try {
            mqttClient = new MqttAsyncClient(config.getBrokerUrl(), config.getClientId(), new MemoryPersistence());

            MqttConnectionOptions options = createConnectOptions();
            IMqttToken token = mqttClient.connect(options);
            token.waitForCompletion();

            log.info("Connected to MQTT v5 broker: {}", config.getBrokerUrl());
        } catch (MqttException e) {
            log.error("Failed to initialize MQTT client", e);
            throw new RuntimeException("MQTT initialization failed", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                log.info("Disconnected from MQTT broker");
            } catch (MqttException e) {
                log.error("Error disconnecting from MQTT broker", e);
            }
        }
    }

    /**
     * Creates MQTT v5 connection options from configuration.
     */
    private MqttConnectionOptions createConnectOptions() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(config.isCleanSession());
        options.setConnectionTimeout(config.getConnectionTimeout());
        options.setKeepAliveInterval(config.getKeepAliveInterval());
        options.setAutomaticReconnect(config.isAutoReconnect());

        if (config.getUsername() != null && !config.getUsername().isEmpty()) {
            options.setUserName(config.getUsername());
        }
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            options.setPassword(config.getPassword().getBytes());
        }

        return options;
    }

    /**
     * Sets up the message callback handler.
     * Must be called before subscribing to topics.
     */
    public void setMessageHandler(java.util.function.BiConsumer<String, MqttMessage> messageHandler) {
        if (mqttClient == null) {
            throw new IllegalStateException("MQTT client not initialized");
        }

        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void disconnected(MqttDisconnectResponse disconnectResponse) {
                // MqttDisconnectResponse contains disconnect reason information
                if (disconnectResponse != null) {
                    log.error("MQTT connection lost. Response: {}", disconnectResponse);
                } else {
                    log.error("MQTT connection lost");
                }
            }

            @Override
            public void mqttErrorOccurred(org.eclipse.paho.mqttv5.common.MqttException exception) {
                log.error("MQTT error occurred", exception);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                log.debug("Received message on topic: {}, payload size: {} bytes",
                        topic, message.getPayload().length);
                messageHandler.accept(topic, message);
            }

            @Override
            public void deliveryComplete(IMqttToken token) {
                // Not used for subscriptions
            }

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("MQTT connection complete. Reconnect: {}, Server: {}", reconnect, serverURI);
            }

            @Override
            public void authPacketArrived(int reasonCode, MqttProperties properties) {
                log.debug("MQTT auth packet arrived: reasonCode={}", reasonCode);
            }
        });
    }

    /**
     * Subscribes to a single topic.
     * 
     * @param topic Topic to subscribe to
     * @param qos Quality of Service level
     */
    public void subscribe(String topic, int qos) throws MqttException {
        if (mqttClient == null || !mqttClient.isConnected()) {
            throw new IllegalStateException("MQTT client not connected");
        }

        IMqttToken token = mqttClient.subscribe(topic, qos);
        token.waitForCompletion();
        log.info("Subscribed to topic: {} with QoS: {}", topic, qos);
    }

    /**
     * Subscribes to the configured BSM ingress topic.
     * 
     * @param messageHandler BiConsumer that processes received messages (topic, message)
     */
    public void subscribeToIngress(java.util.function.BiConsumer<String, MqttMessage> messageHandler) throws MqttException {
        setMessageHandler(messageHandler);
        subscribe(config.getSubscribeTopic(), config.getQos());
    }

    /**
     * Subscribes to geohash-filtered topics (from TMC apps).
     * 
     * @param messageHandler BiConsumer that processes received messages (topic, message)
     */
    public void subscribeToGeohashFiltered(java.util.function.BiConsumer<String, MqttMessage> messageHandler) throws MqttException {
        setMessageHandler(messageHandler);
        subscribe(config.getGeohashFilteredTopicPattern(), config.getQos());
    }

    /**
     * Publishes a message to the broker.
     * 
     * @param topic   MQTT topic to publish to
     * @param payload Message payload
     * @param qos     Quality of Service level
     */
    public void publish(String topic, byte[] payload, int qos) throws MqttException {
        if (mqttClient == null || !mqttClient.isConnected()) {
            throw new IllegalStateException("MQTT client not connected");
        }

        MqttMessage message = new MqttMessage(payload);
        message.setQos(qos);
        message.setRetained(false);

        IMqttToken token = mqttClient.publish(topic, message);
        token.waitForCompletion();
        log.debug("Published message to topic: {}, payload size: {} bytes",
                topic, payload.length);
    }

    /**
     * Publishes a message to the broker using configured QoS.
     */
    public void publish(String topic, byte[] payload) throws MqttException {
        publish(topic, payload, config.getQos());
    }

    /**
     * Checks if MQTT client is connected.
     */
    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }
}
