package com.neaera.cvmec.kafkaproducer.services;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PostgresNotificationService {

    private static final String CHANNEL_NAME = "table_updates";
    private static final int NOTIFICATION_CHECK_INTERVAL_MS = 500;

    private final DataSource dataSource;
    private final ApplicationEventPublisher eventPublisher;

    private Connection listenerConnection;
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private CompletableFuture<Void> listenerTask;

    @Autowired
    public PostgresNotificationService(DataSource dataSource, ApplicationEventPublisher eventPublisher) {
        this.dataSource = dataSource;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void startListening() {
        try {
            // Create a dedicated connection for listening
            listenerConnection = dataSource.getConnection();
            listenerConnection.setAutoCommit(true);

            // Start listening to the channel
            try (Statement stmt = listenerConnection.createStatement()) {
                stmt.execute("LISTEN " + CHANNEL_NAME);
                log.info("Started listening to PostgreSQL channel: {}", CHANNEL_NAME);
            }

            // Start the async listener task
            listening.set(true);
            listenerTask = CompletableFuture.runAsync(this::listenForNotifications);

        } catch (SQLException e) {
            log.error("Failed to start PostgreSQL notification listener", e);
            throw new RuntimeException("Failed to start PostgreSQL notification listener", e);
        }
    }

    @PreDestroy
    public void stopListening() {
        listening.set(false);

        if (listenerTask != null) {
            listenerTask.cancel(true);
        }

        if (listenerConnection != null) {
            try {
                try (Statement stmt = listenerConnection.createStatement()) {
                    stmt.execute("UNLISTEN " + CHANNEL_NAME);
                }
                listenerConnection.close();
                log.info("Stopped listening to PostgreSQL channel: {}", CHANNEL_NAME);
            } catch (SQLException e) {
                log.warn("Error while stopping PostgreSQL listener", e);
            }
        }
    }

    private void listenForNotifications() {
        log.info("PostgreSQL notification listener thread started");

        try {
            PGConnection pgConnection = listenerConnection.unwrap(PGConnection.class);

            while (listening.get() && !Thread.currentThread().isInterrupted()) {
                // Check for notifications
                PGNotification[] notifications = pgConnection.getNotifications();

                if (notifications != null && notifications.length > 0) {
                    for (PGNotification notification : notifications) {
                        handleNotification(notification);
                    }
                } else {
                    // Sleep briefly if no notifications received
                    try {
                        Thread.sleep(NOTIFICATION_CHECK_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (SQLException e) {
            if (listening.get()) {
                log.error("Error in PostgreSQL notification listener", e);
            }
        }

        log.info("PostgreSQL notification listener thread stopped");
    }

    private void handleNotification(PGNotification notification) {
        log.debug("Received PostgreSQL notification on channel '{}': {}",
                notification.getName(), notification.getParameter());

        if (CHANNEL_NAME.equals(notification.getName())) {
            // Publish an application event that the task will listen to
            eventPublisher.publishEvent(new TableUpdateNotificationEvent(notification.getParameter()));
            log.debug("Published TableUpdateNotificationEvent");
        }
    }

    /**
     * Application event representing a table update notification
     */
    public static class TableUpdateNotificationEvent {
        private final String payload;

        public TableUpdateNotificationEvent(String payload) {
            this.payload = payload;
        }

        public String getPayload() {
            return payload;
        }
    }
}