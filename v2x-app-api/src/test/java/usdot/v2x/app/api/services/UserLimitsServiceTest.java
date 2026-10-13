package usdot.v2x.app.api.services;

import usdot.v2x.app.api.models.UserLimits;
import usdot.v2x.app.api.repositories.UserLimitsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserLimitsServiceTest {

    @Mock
    private UserLimitsRepository userLimitsRepository;

    @InjectMocks
    private UserLimitsService userLimitsService;

    @Test
    void createOrUpdateUserLimits_updatesInactiveRowInsteadOfInserting() {
        UserLimits inactive = new UserLimits();
        inactive.setId(42L);
        inactive.setUsername("user");
        inactive.setVendorId("MergentPlatform");
        inactive.setMaxRegistrations(5);
        inactive.setIsActive(false);
        inactive.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        inactive.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        when(userLimitsRepository.findByUsernameAndVendorIdIgnoreActive("user", "MergentPlatform"))
                .thenReturn(Optional.of(inactive));
        when(userLimitsRepository.save(any(UserLimits.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserLimits saved = userLimitsService.createOrUpdateUserLimits("user", "MergentPlatform", 5, "system");

        assertTrue(saved.getIsActive());
        assertEquals(5, saved.getMaxRegistrations());
        assertEquals(42L, saved.getId());

        ArgumentCaptor<UserLimits> captor = ArgumentCaptor.forClass(UserLimits.class);
        verify(userLimitsRepository, times(1)).save(captor.capture());
        assertEquals(42L, captor.getValue().getId());
    }

    @Test
    void existsIncludingInactive_delegatesToIgnoreActiveLookup() {
        when(userLimitsRepository.existsByUsernameAndVendorIdIgnoreActive("user", "MergentPlatform"))
                .thenReturn(true);

        assertTrue(userLimitsService.existsIncludingInactive("user", "MergentPlatform"));
        verify(userLimitsRepository).existsByUsernameAndVendorIdIgnoreActive("user", "MergentPlatform");
    }

    @Test
    void reactivateIfInactive_reactivatesWithoutChangingLimit() {
        UserLimits inactive = new UserLimits();
        inactive.setId(42L);
        inactive.setUsername("user");
        inactive.setVendorId("MergentPlatform");
        inactive.setMaxRegistrations(7);
        inactive.setIsActive(false);

        when(userLimitsRepository.findByUsernameAndVendorIdIgnoreActive("user", "MergentPlatform"))
                .thenReturn(Optional.of(inactive));
        when(userLimitsRepository.save(any(UserLimits.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userLimitsService.reactivateIfInactive("user", "MergentPlatform", "system");

        ArgumentCaptor<UserLimits> captor = ArgumentCaptor.forClass(UserLimits.class);
        verify(userLimitsRepository).save(captor.capture());
        assertTrue(captor.getValue().getIsActive());
        assertEquals(7, captor.getValue().getMaxRegistrations());
    }

    @Test
    void reactivateIfInactive_skipsAlreadyActiveRow() {
        UserLimits active = new UserLimits();
        active.setUsername("user");
        active.setVendorId("MergentPlatform");
        active.setIsActive(true);

        when(userLimitsRepository.findByUsernameAndVendorIdIgnoreActive("user", "MergentPlatform"))
                .thenReturn(Optional.of(active));

        userLimitsService.reactivateIfInactive("user", "MergentPlatform", "system");

        verify(userLimitsRepository, never()).save(any(UserLimits.class));
    }
}
