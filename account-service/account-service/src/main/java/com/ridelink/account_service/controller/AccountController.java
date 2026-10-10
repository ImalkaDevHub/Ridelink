package com.ridelink.account_service.controller;

import com.ridelink.account_service.dto.AccountResponse;
import com.ridelink.account_service.dto.AuthResponse;
import com.ridelink.account_service.dto.LoginRequest;
import com.ridelink.account_service.dto.PagedResponse;
import com.ridelink.account_service.dto.PasswordChangeRequest;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AccountController {

    // Owner-or-admin rule for per-account endpoints. The authenticated
    // principal's name is the caller's account id (see JwtAuthFilter).
    private static final String OWNER_OR_ADMIN = "hasAuthority('ADMIN') or #id == authentication.name";

    @Autowired
    private AccountService accountService;

    // Controller methods return ResponseEntity<?> because a single method
    // can return either the success DTO or an error body, depending on
    // which exception (if any) is caught - springdoc can't infer a schema
    // from a wildcard generic type, so each @ApiResponse below spells out
    // the real status code and schema explicitly instead of leaving
    // Swagger UI to guess "200, generic object" for everything.

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
            return errorResponse(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", e.getMessage());
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

    @GetMapping("/api/auth/me")
    @ApiResponse(responseCode = "200", description = "The caller's own profile",
            content = @Content(schema = @Schema(implementation = AccountResponse.class)))
    @ApiResponse(responseCode = "401", description = "No valid access token")
    @ApiResponse(responseCode = "404", description = "The token's account no longer exists")
    public ResponseEntity<?> me(Authentication authentication) {
        try {
            return ResponseEntity.ok(accountService.getProfile(authentication.getName()));
        } catch (AccountService.AccountNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
        }
    }

    @GetMapping("/api/accounts")
    @PreAuthorize("hasAuthority('ADMIN')")
    @ApiResponse(responseCode = "200", description = "One page of accounts, sorted by id")
    @ApiResponse(responseCode = "403", description = "Caller does not have the ADMIN authority")
    public ResponseEntity<PagedResponse<AccountResponse>> listAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(accountService.listAccounts(page, size));
    }

    @GetMapping("/api/accounts/{id}")
    @PreAuthorize(OWNER_OR_ADMIN)
    @ApiResponse(responseCode = "200", description = "Profile found - never includes the password",
            content = @Content(schema = @Schema(implementation = AccountResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is neither the owner nor an ADMIN")
    @ApiResponse(responseCode = "404", description = "No account with this id")
    public ResponseEntity<?> getProfile(@PathVariable String id) {
        try {
            return ResponseEntity.ok(accountService.getProfile(id));
        } catch (AccountService.AccountNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
        }
    }

    @PatchMapping("/api/accounts/{id}")
    @PreAuthorize(OWNER_OR_ADMIN)
    @ApiResponse(responseCode = "200", description = "Profile updated",
            content = @Content(schema = @Schema(implementation = AccountResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is neither the owner nor an ADMIN")
    @ApiResponse(responseCode = "404", description = "No account with this id")
    @ApiResponse(responseCode = "409", description = "Email already used by another account")
    public ResponseEntity<?> updateProfile(@PathVariable String id, @Valid @RequestBody ProfileUpdateRequest request) {
        try {
            return ResponseEntity.ok(accountService.updateProfile(id, request));
        } catch (AccountService.AccountNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
        } catch (AccountService.EmailAlreadyExistsException e) {
            return errorResponse(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", e.getMessage());
        }
    }

    // Owner only - not even an ADMIN may change someone else's password.
    @PatchMapping("/api/accounts/{id}/password")
    @PreAuthorize("#id == authentication.name")
    @ApiResponse(responseCode = "200", description = "Password updated")
    @ApiResponse(responseCode = "400", description = "Validation failed, current password wrong, or new password unchanged")
    @ApiResponse(responseCode = "403", description = "Caller is not the owner of this account")
    @ApiResponse(responseCode = "404", description = "No account with this id")
    public ResponseEntity<?> changePassword(@PathVariable String id, @Valid @RequestBody PasswordChangeRequest request) {
        try {
            accountService.changePassword(id, request);
            return ResponseEntity.ok(Map.of("message", "Password updated"));
        } catch (AccountService.AccountNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
        } catch (AccountService.WrongPasswordException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", e.getMessage());
        } catch (AccountService.SamePasswordException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, "SAME_PASSWORD", e.getMessage());
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

    @PatchMapping("/api/accounts/{id}/role")
    @PreAuthorize("hasAuthority('ADMIN')")
    @ApiResponse(responseCode = "200", description = "Role updated",
            content = @Content(schema = @Schema(implementation = AccountResponse.class)))
    @ApiResponse(responseCode = "400", description = "Role must be PASSENGER, DRIVER or ADMIN")
    @ApiResponse(responseCode = "403", description = "Caller does not have the ADMIN authority")
    @ApiResponse(responseCode = "404", description = "No account with this id")
    public ResponseEntity<?> setRole(@PathVariable String id, @RequestParam String role) {
        try {
            return ResponseEntity.ok(accountService.setRole(id, role));
        } catch (AccountService.AccountNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
        } catch (AccountService.InvalidRoleException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_ROLE", e.getMessage());
        }
    }

    private ResponseEntity<Map<String, String>> errorResponse(HttpStatus status, String code, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
