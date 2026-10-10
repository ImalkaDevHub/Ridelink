package com.ridelink.driver_service.security;

import com.ridelink.driver_service.repository.DriverRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

// Used from @PreAuthorize as @driverAccess.isOwner(#id, authentication):
// true when the driver profile belongs to the caller's account. The
// authenticated principal's name is the account id (see JwtAuthFilter).
// Profiles created before accountId was recorded have no owner, so only an
// ADMIN can modify them.
@Component("driverAccess")
public class DriverAccess {

    @Autowired
    private DriverRepository driverRepository;

    public boolean isOwner(String driverId, Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return false;
        }
        return driverRepository.findById(driverId)
                .map(driver -> authentication.getName().equals(driver.getAccountId()))
                .orElse(false);
    }
}
