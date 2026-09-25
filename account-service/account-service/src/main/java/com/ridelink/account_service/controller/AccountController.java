package com.ridelink.account_service.controller;

import com.ridelink.account_service.dto.AccountResponse;
import com.ridelink.account_service.dto.AuthResponse;
import com.ridelink.account_service.dto.LoginRequest;
import com.ridelink.account_service.dto.ProfileUpdateRequest;
import com.ridelink.account_service.dto.RegisterRequest;
import com.ridelink.account_service.service.AccountService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AccountController {

    @Autowired
    private AccountService accountService;

    // Controller methods return ResponseEntity<?> because a single method

    // Public - no token exists yet at registration time. Overrides the
    // service-wide default security requirement so Swagger UI doesn't show
    // a misleading lock icon here.
    @PostMapping("/api/auth/register")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Account created",
            content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed or role is not PASSENGER/DRIVER")
    @ApiResponse(responseCode = "409", description = "Email already registered")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(accountService.register(request));
        } catch (AccountService.EmailAlreadyExistsException e) {
            return errorResponse(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", e.getMessage());
        } catch (AccountService.InvalidRoleException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_ROLE", e.getMessage());
        }
    }

    // Public - this is how a token is obtained in the first place.
    @PostMapping("/api/auth/login")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Login succeeded",
            content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    @ApiResponse(responseCode = "403", description = "Account is suspended")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(accountService.login(request));
        } catch (AccountService.InvalidCredentialsException e) {
            return errorResponse(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", e.getMessage());
        } catch (AccountService.AccountSuspendedException e) {
            return errorResponse(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", e.getMessage());
        }
    }

    @GetMapping("/api/accounts/{id}")
    @ApiResponse(responseCode = "200", description = "Profile found - never includes the password",
            content = @Content(schema = @Schema(implementation = AccountResponse.class)))
    @ApiResponse(responseCode = "404", description = "No account with this id")
    public ResponseEntity<?> getProfile(@PathVariable String id) {
        try {
            return ResponseEntity.ok(accountService.getProfile(id));
        } catch (AccountService.AccountNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
        }
    }

    @PatchMapping("/api/accounts/{id}")
    @ApiResponse(responseCode = "200", description = "Profile updated",
            content = @Content(schema = @Schema(implementation = AccountResponse.class)))
    @ApiResponse(responseCode = "404", description = "No account with this id")
    @ApiResponse(responseCode = "409", description = "Email already used by another account")
    public ResponseEntity<?> updateProfile(@PathVariable String id, @Valid @RequestBody ProfileUpdateRequest request) {
        try {
            return ResponseEntity.ok(accountService.updateProfile(id, request));
        } catch (AccountService.AccountNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
        } catch (AccountService.EmailAlreadyExistsException e) {
            return errorResponse(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", e.getMessage());
        }
    }

    @PatchMapping("/api/accounts/{id}/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    @ApiResponse(responseCode = "200", description = "Status updated",
            content = @Content(schema = @Schema(implementation = AccountResponse.class)))
    @ApiResponse(responseCode = "400", description = "Status must be ACTIVE or SUSPENDED")
    @ApiResponse(responseCode = "403", description = "Caller does not have the ADMIN authority")
    @ApiResponse(responseCode = "404", description = "No account with this id")
    public ResponseEntity<?> setStatus(@PathVariable String id, @RequestParam String status) {
        try {
            return ResponseEntity.ok(accountService.setStatus(id, status));
        } catch (AccountService.AccountNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
        } catch (AccountService.InvalidStatusException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_STATUS", e.getMessage());
        }
    }

}
