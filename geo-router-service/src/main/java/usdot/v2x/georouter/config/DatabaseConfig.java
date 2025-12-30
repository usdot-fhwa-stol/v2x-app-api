package usdot.v2x.georouter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * Database configuration for geo-router service.
 * Connects to the v2x-app-api PostgreSQL database to query MQTT device locations.
 */
@Configuration
public class DatabaseConfig {

    @Value("${geo-routing.database.url:jdbc:postgresql://localhost:5432/v2x_app_db}")
    private String databaseUrl;

    @Value("${geo-routing.database.username:admin_user}")
    private String databaseUsername;

    @Value("${geo-routing.database.password:change_me_123}")
    private String databasePassword;

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(databaseUrl);
        dataSource.setUsername(databaseUsername);
        dataSource.setPassword(databasePassword);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}

