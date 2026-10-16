package usdot.v2x.app.api.models;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

class UserLimitsSchemaTest {

    @Test
    void generatedSchemaAllowsMultipleVendorsButRejectsDuplicateUserVendorPairs() throws Exception {
        String url = "jdbc:h2:mem:user_limits_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url", url)
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                .build();
        try (var factory = new MetadataSources(registry).addAnnotatedClass(UserLimits.class)
                .buildMetadata().buildSessionFactory();
                var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement()) {
            String insert = "INSERT INTO user_limits "
                    + "(username, vendor_id, max_registrations, is_active, created_at, updated_at) "
                    + "VALUES ('user', '%s', 5, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
            statement.executeUpdate(insert.formatted("vendor-a"));
            assertDoesNotThrow(() -> statement.executeUpdate(insert.formatted("vendor-b")));

            SQLException duplicate = assertThrows(SQLException.class,
                    () -> statement.executeUpdate(insert.formatted("vendor-a")));
            assertEquals("23505", duplicate.getSQLState(), "Inactive rows must also reserve the user/vendor pair");
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
