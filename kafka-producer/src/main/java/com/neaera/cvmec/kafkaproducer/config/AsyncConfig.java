package com.neaera.cvmec.kafkaproducer.config;

import com.neaera.cvmec.kafkaproducer.tasks.PullGeoMessagesTask;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Autowired
    private PullGeoMessagesTask pullGeoMessagesTask;

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-notification-");
        executor.initialize();
        return executor;
    }

    /**
     * Perform initial cache load when the application is ready. Cached messages are
     * published to Kafka on a 1Hz schedule by {@code PublishGeoMessagesTask}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready, performing initial cache load");
        pullGeoMessagesTask.refreshGeohashPayloadMessages();
    }
}