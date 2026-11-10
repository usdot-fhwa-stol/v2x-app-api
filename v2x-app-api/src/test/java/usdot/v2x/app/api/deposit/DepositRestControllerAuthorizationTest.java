package usdot.v2x.app.api.deposit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import usdot.v2x.app.api.models.etx.configuration.DepositRequest;

class DepositRestControllerAuthorizationTest {

    @Test
    void geofencePreviewRequiresDepositRole() throws NoSuchMethodException {
        PreAuthorize authorization = DepositRestController.class
                .getMethod("previewGeofenceGeohashes", DepositRequest.class, boolean.class)
                .getAnnotation(PreAuthorize.class);

        assertNotNull(authorization);
        assertEquals("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR')", authorization.value());
    }
}
