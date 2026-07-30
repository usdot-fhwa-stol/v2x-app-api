package com.neaera.cvmec.kafkaproducer;

import com.neaera.cvmec.kafkaproducer.services.PostgresNotificationService;
import com.neaera.cvmec.kafkaproducer.services.PostgresService;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;

@SpringBootTest
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class
})
class KafkaProducerApplicationTests {

    @MockitoBean
    private PostgresNotificationService postgresNotificationService;

    @MockitoBean
    private PostgresService postgresService;

    @BeforeEach
    void setUp() {
        when(postgresService.getGeohashPayloadMessages()).thenReturn(Collections.emptyList());
    }

    @Test
    void contextLoads() {
    }

}
