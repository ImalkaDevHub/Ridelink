package com.ridelink.account_service.controller;

import com.ridelink.account_service.dto.LoginRequest;
import com.ridelink.account_service.dto.ProfileUpdateRequest;
import com.ridelink.account_service.dto.RegisterRequest;
import com.ridelink.account_service.service.AccountService;
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

    @PostMapping("/api/auth/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(accountService.register(request));
        } catch (AccountService.EmailAlreadyExistsException e) {
            return errorResponse(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", e.getMessage());
        } catch (AccountService.InvalidRoleException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_ROLE", e.getMessage());
        }
    }

    @PostMapping("/api/auth/login")
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
    public ResponseEntity<?> getProfile(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(accountService.getProfile(id));
        } catch (AccountService.AccountNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
        }
    }

    @PatchMapping("/api/accounts/{id}")
    public ResponseEntity<?> updateProfile(@PathVariable Long id, @Valid @RequestBody ProfileUpdateRequest request) {
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
    public ResponseEntity<?> setStatus(@PathVariable Long id, @RequestParam String status) {
        try {
            return ResponseEntity.ok(accountService.setStatus(id, status));
        } catch (AccountService.AccountNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
        } catch (AccountService.InvalidStatusException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_STATUS", e.getMessage());
        }
    }

    private ResponseEntity<Map<String, String>> errorResponse(HttpStatus status, String code, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
