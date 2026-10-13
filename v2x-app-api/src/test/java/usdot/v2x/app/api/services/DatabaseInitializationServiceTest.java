package usdot.v2x.app.api.services;

import usdot.v2x.app.api.config.etx.EtxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseInitializationServiceTest {

    @Mock
    private VendorLimitsService vendorLimitsService;

    @Mock
    private UserLimitsService userLimitsService;

    @Mock
    private EtxProperties etxProperties;

    @InjectMocks
    private DatabaseInitializationService databaseInitializationService;

    @BeforeEach
    void setUp() {
        EtxProperties.VendorConfig device = new EtxProperties.VendorConfig();
        device.setId("MergentDevice");
        device.setLimit(100);

        EtxProperties.VendorConfig platform = new EtxProperties.VendorConfig();
        platform.setId("MergentPlatform");
        platform.setLimit(100);

        EtxProperties.RegistrationLimits registration = new EtxProperties.RegistrationLimits();
        registration.setVendors(List.of(device, platform));
        when(etxProperties.getRegistration()).thenReturn(registration);

        when(vendorLimitsService.hasCustomLimits(anyString())).thenReturn(true);
    }

    @Test
    void run_skipsUserLimitsWhenInactiveRowAlreadyExists() throws Exception {
        when(userLimitsService.existsIncludingInactive(anyString(), anyString())).thenReturn(true);

        databaseInitializationService.run(new DefaultApplicationArguments());

        verify(userLimitsService, never()).createOrUpdateUserLimits(anyString(), anyString(), anyInt(), anyString());
        verify(userLimitsService, atLeastOnce()).existsIncludingInactive("user", "MergentPlatform");
        verify(userLimitsService, never()).reactivateUserLimitsForVendor(anyString(), anyString());
        verifyNoMoreInteractions(userLimitsService);
    }

    @Test
    void run_doesNotRethrowWhenSeedingFails() throws Exception {
        when(userLimitsService.existsIncludingInactive(anyString(), anyString()))
                .thenThrow(new RuntimeException("duplicate key"));

        databaseInitializationService.run(new DefaultApplicationArguments());
    }
}
