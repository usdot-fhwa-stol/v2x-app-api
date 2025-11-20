package usdot.v2x.app.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(info = @Info(title = "V2X App API", version = "1.0.0", description = "API for Vehicle-to-Everything (V2X) Multi-Access Edge Computing (MEC) services. "
        +
        "This App API provides endpoints for ETX registration, V2X Message Deposit, " +
        "and authentication services."), tags = {
                @Tag(name = "Authentication", description = "Authentication and authorization for Registration and Deposit endpoints"),
                @Tag(name = "Deposit", description = "V2X Message Deposit endpoints for deployment to the ETX MQTT Broker"),
                @Tag(name = "Registration", description = "ETX client registration endpoints for the MQTT Broker")
        }, security = {
                @SecurityRequirement(name = "BearerAuth")
        })
@SecurityScheme(name = "BearerAuth", type = SecuritySchemeType.HTTP, bearerFormat = "JWT", scheme = "bearer", description = "Keycloak JWT Bearer Token. Required for all endpoints except decode endpoints. "
        +
        "Obtain a token from the /auth/token endpoint using your credentials.")
public class V2XAppApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(V2XAppApiApplication.class, args);
    }

}
