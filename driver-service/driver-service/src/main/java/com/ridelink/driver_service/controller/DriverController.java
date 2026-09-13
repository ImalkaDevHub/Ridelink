package com.ridelink.driver_service.controller;

import com.ridelink.driver_service.model.Driver;
import com.ridelink.driver_service.service.DriverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    @Autowired
    private DriverService driverService;

    @PostMapping
    public Driver create(@RequestBody Driver driver) {
        return driverService.createDriver(driver);
    }

    @GetMapping("/available")
    public List<Driver> getAvailable() {
        return driverService.getAvailableDrivers();
    }

    // Dual-purpose endpoint: called by a driver toggling their own
    // availability (requires the DRIVER role) AND by ride-service's
    // DriverClient with no token at all, to reserve a driver on ride
    // assignment. isAnonymous() lets that existing interservice call
    // through unchanged while still rejecting a non-DRIVER token (e.g. a
    // PASSENGER's) with 403.
    @PatchMapping("/{id}/availability")
    @PreAuthorize("isAnonymous() or hasAuthority('DRIVER')")
    public Driver setAvailability(@PathVariable Long id, @RequestParam boolean available) {
        return driverService.setAvailability(id, available);
    }
}