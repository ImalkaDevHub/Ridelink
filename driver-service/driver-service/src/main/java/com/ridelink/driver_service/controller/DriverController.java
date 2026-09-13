package com.ridelink.driver_service.controller;

import com.ridelink.driver_service.model.Driver;
import com.ridelink.driver_service.service.DriverService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    @Autowired
    private DriverService driverService;

    @PostMapping
    public ResponseEntity<Driver> create(@Valid @RequestBody Driver driver) {
        return ResponseEntity.status(HttpStatus.CREATED).body(driverService.createDriver(driver));
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
    public ResponseEntity<?> setAvailability(@PathVariable Long id, @RequestParam boolean available) {
        try {
            return ResponseEntity.ok(driverService.setAvailability(id, available));
        } catch (DriverService.DriverNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "DRIVER_NOT_FOUND", e.getMessage());
        }
    }

    private ResponseEntity<Map<String, String>> errorResponse(HttpStatus status, String code, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
