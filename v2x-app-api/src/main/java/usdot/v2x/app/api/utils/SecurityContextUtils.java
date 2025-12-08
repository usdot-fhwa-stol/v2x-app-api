package usdot.v2x.app.api.utils;

import lombok.extern.slf4j.Slf4j;
import usdot.v2x.app.api.config.etx.EtxProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Utility class for extracting user information from Spring Security context
 */
@Slf4j
@Component
public class SecurityContextUtils {

    @Autowired
    private EtxProperties etxProperties;

    /**
     * Determines the vendor ID based on the current user's authentication context
     * 
     * @return vendor ID string
     */
    public String determineVendorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null) {
            if (authentication.getAuthorities() != null) {
                boolean isDepositor = authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(authority -> "ROLE_DEPOSITOR".equals(authority));

                if (isDepositor) {
                    log.debug("Using depositor vendor ID for ROLE_DEPOSITOR user");
                    return etxProperties.getDepositorVendorId();
                }
            }
        } else {
            log.warn("Authentication is null in SecurityContext");
        }
        log.debug("Using default vendor ID for user");
        return etxProperties.getVendorId();
    }

    /**
     * Determines the requested by user based on the current user's authentication
     * context
     * 
     * @return username string
     */
    public String determineRequestedBy() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null) {
            if (authentication.getAuthorities() != null) {
                return authentication.getName();
            }
        } else {
            log.warn("Authentication is null in SecurityContext");
        }
        log.debug("Using default requested by for user");
        return "unknown";
    }

    /**
     * Gets the current authenticated username
     * 
     * @return username string or "unknown" if not authenticated
     */
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }

        return "unknown";
    }

    /**
     * Checks if the current user has a specific role
     * 
     * @param role the role to check for
     * @return true if user has the role, false otherwise
     */
    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getAuthorities() != null) {
            return authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(authority -> role.equals(authority));
        }

        return false;
    }

    /**
     * Checks if the current user is a depositor
     * 
     * @return true if user has ROLE_DEPOSITOR, false otherwise
     */
    public boolean isDepositor() {
        return hasRole("ROLE_DEPOSITOR");
    }

    /**
     * Checks if the current user is an admin
     * 
     * @return true if user has ROLE_ADMIN, false otherwise
     */
    public boolean isAdmin() {
        return hasRole("ROLE_ADMIN");
    }
}
