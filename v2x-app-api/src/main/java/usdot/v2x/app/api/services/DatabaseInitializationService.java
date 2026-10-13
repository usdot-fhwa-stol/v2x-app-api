package usdot.v2x.app.api.services;

import lombok.extern.slf4j.Slf4j;
import usdot.v2x.app.api.config.etx.EtxProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Service for initializing database with default configuration data.
 */
@Slf4j
@Service
public class DatabaseInitializationService implements ApplicationRunner {

    @Autowired
    private VendorLimitsService vendorLimitsService;

    @Autowired
    private UserLimitsService userLimitsService;

    @Autowired
    private EtxProperties etxProperties;

    @Override
    @ConditionalOnProperty(value = { "etx.enabled" }, havingValue = "true")
    public void run(ApplicationArguments args) throws Exception {
        log.info("Starting database initialization...");

        try {
            initializeVendorLimits();
            initializeUserLimits();
            log.info("Database initialization completed successfully");
        } catch (Exception e) {
            // Seeding must not take down the API. Existing unique rows are expected on restart.
            log.error("Failed to initialize database: {}", e.getMessage(), e);
        }
    }

    /**
     * Initialize vendor limits from configuration
     */
    private void initializeVendorLimits() {
        log.info("Initializing vendor limits...");

        if (etxProperties.getRegistration().getVendors() != null) {
            for (EtxProperties.VendorConfig vendor : etxProperties.getRegistration().getVendors()) {
                // Check if vendor limits already exist
                if (!vendorLimitsService.hasCustomLimits(vendor.getId())) {
                    vendorLimitsService.createOrUpdateVendorLimits(
                            vendor.getId(),
                            vendor.getLimit(),
                            "system");
                    log.info("Created default vendor limits for {}: {} registrations",
                            vendor.getId(), vendor.getLimit());
                } else {
                    log.debug("Vendor limits already exist for {}", vendor.getId());
                }
            }
        }
    }

    /**
     * Initialize default user limits for standard roles
     */
    private void initializeUserLimits() {
        log.info("Initializing user limits...");

        // Default user limits for standard roles
        String[] defaultUsers = { "user", "depositor", "admin" };
        int[] defaultLimits = { 5, 10, 50 }; // user: 5, depositor: 10, admin: 50

        if (etxProperties.getRegistration().getVendors() != null) {
            for (EtxProperties.VendorConfig vendor : etxProperties.getRegistration().getVendors()) {
                for (int i = 0; i < defaultUsers.length; i++) {
                    String username = defaultUsers[i];
                    int limit = defaultLimits[i];

                    // Unique constraint is on (username, vendor_id), including soft-deleted rows
                    if (!userLimitsService.existsIncludingInactive(username, vendor.getId())) {
                        userLimitsService.createOrUpdateUserLimits(
                                username,
                                vendor.getId(),
                                limit,
                                "system");
                        log.info("Created default user limits for {} on vendor {}: {} registrations",
                                username, vendor.getId(), limit);
                    } else {
                        userLimitsService.reactivateIfInactive(username, vendor.getId(), "system");
                        log.debug("User limits already exist for {} on vendor {}", username, vendor.getId());
                    }
                }
            }
        }
    }
}
