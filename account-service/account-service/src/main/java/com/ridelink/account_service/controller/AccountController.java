package com.ridelink.account_service.controller;

import com.ridelink.account_service.dto.AccountResponse;
import com.ridelink.account_service.dto.AuthResponse;
import com.ridelink.account_service.dto.LoginRequest;
import com.ridelink.account_service.dto.PagedResponse;
import com.ridelink.account_service.dto.PasswordChangeRequest;
import com.ridelink.account_service.dto.ProfileUpdateRequest;
import com.ridelink.account_service.dto.RegisterRequest;
import com.ridelink.account_service.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import static com.ridelink.account_service.config.ApiExamples.*;

@RestController
@Tag(name = "Accounts", description = "Registration, login, profiles, passwords and admin account management")
public class AccountController {

    // Owner-or-admin rule for per-account endpoints. The authenticated
    // principal's name is the caller's account id (see JwtAuthFilter).
    private static final String OWNER_OR_ADMIN = "hasAuthority('ADMIN') or #id == authentication.name";

    private static final String JSON = "application/json";

    @Autowired
    private AccountService accountService;

    // Controller methods return ResponseEntity<?> because a single method
    // can return either the success DTO or an error body, depending on
    // which exception (if any) is caught - springdoc can't infer a schema
    // from a wildcard generic type, so each @ApiResponse below spells out
    // the real status code, schema and an example instead of leaving
    // Swagger UI to guess "200, generic object" for everything.

    // Public - no token exists yet at registration time. Overrides the
    // service-wide default security requirement so Swagger UI doesn't show
    // a misleading lock icon here.
    @PostMapping("/api/auth/register")
    @SecurityRequirements
    @Operation(summary = "Register a passenger or driver account",
            description = "Public. Creates a PASSENGER (default) or DRIVER account and returns a JWT. "
                    + "ADMIN accounts cannot self-register. Password: 8-72 characters with a letter and a digit.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = JSON, examples = @ExampleObject(name = "passenger", value = REGISTER_REQUEST))))
    @ApiResponse(responseCode = "201", description = "Account created",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = AuthResponse.class),
                    examples = @ExampleObject(value = AUTH_RESPONSE)))
    @ApiResponse(responseCode = "400", description = "Validation failed or role is not PASSENGER/DRIVER",
            content = @Content(mediaType = JSON, examples = {
                    @ExampleObject(name = "validation", value = VALIDATION_ERROR),
                    @ExampleObject(name = "role", value = INVALID_ROLE)}))
    @ApiResponse(responseCode = "409", description = "Email already registered",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = EMAIL_ALREADY_EXISTS)))
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
    @Operation(summary = "Log in and receive a JWT",
            description = "Public. Unknown email and wrong password give the same error. Suspended accounts are refused.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = JSON, examples = @ExampleObject(value = LOGIN_REQUEST))))
    @ApiResponse(responseCode = "200", description = "Login succeeded",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = AuthResponse.class),
                    examples = @ExampleObject(value = AUTH_RESPONSE)))
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = VALIDATION_ERROR)))
    @ApiResponse(responseCode = "401", description = "Invalid email or password",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = INVALID_CREDENTIALS)))
    @ApiResponse(responseCode = "403", description = "Account is suspended",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = ACCOUNT_SUSPENDED)))
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
    @Operation(summary = "Get the caller's own profile", description = "Any authenticated user.")
    @ApiResponse(responseCode = "200", description = "The caller's own profile",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = AccountResponse.class),
                    examples = @ExampleObject(value = ACCOUNT_RESPONSE)))
    @ApiResponse(responseCode = "401", description = "No valid access token",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = UNAUTHORIZED)))
    @ApiResponse(responseCode = "404", description = "The token's account no longer exists",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = ACCOUNT_NOT_FOUND)))
    public ResponseEntity<?> me(@Parameter(hidden = true) Authentication authentication) {
        try {
            return ResponseEntity.ok(accountService.getProfile(authentication.getName()));
        } catch (AccountService.AccountNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
        }
    }

    @GetMapping("/api/accounts")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "List accounts (paged)", description = "ADMIN only. Sorted by id; size is capped at 100.")
    @ApiResponse(responseCode = "200", description = "One page of accounts",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = ACCOUNT_PAGE)))
    @ApiResponse(responseCode = "401", description = "No valid access token",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = UNAUTHORIZED)))
    @ApiResponse(responseCode = "403", description = "Caller does not have the ADMIN authority",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    public ResponseEntity<PagedResponse<AccountResponse>> listAccounts(
            @Parameter(description = "Zero-based page number", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1-100)", example = "20") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(accountService.listAccounts(page, size));
    }

    @GetMapping("/api/accounts/{id}")
    @PreAuthorize(OWNER_OR_ADMIN)
    @Operation(summary = "Get a profile", description = "The account owner or an ADMIN. Never includes the password.")
    @ApiResponse(responseCode = "200", description = "Profile found",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = AccountResponse.class),
                    examples = @ExampleObject(value = ACCOUNT_RESPONSE)))
    @ApiResponse(responseCode = "401", description = "No valid access token",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = UNAUTHORIZED)))
    @ApiResponse(responseCode = "403", description = "Caller is neither the owner nor an ADMIN",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    @ApiResponse(responseCode = "404", description = "No account with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = ACCOUNT_NOT_FOUND)))
    public ResponseEntity<?> getProfile(@PathVariable String id) {
        try {
            return ResponseEntity.ok(accountService.getProfile(id));
        } catch (AccountService.AccountNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
        }
    }

    @PatchMapping("/api/accounts/{id}")
    @PreAuthorize(OWNER_OR_ADMIN)
    @Operation(summary = "Update a profile (name and email)", description = "The account owner or an ADMIN.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = JSON, examples = @ExampleObject(value = PROFILE_UPDATE_REQUEST))))
    @ApiResponse(responseCode = "200", description = "Profile updated",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = AccountResponse.class),
                    examples = @ExampleObject(value = ACCOUNT_RESPONSE)))
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = VALIDATION_ERROR)))
    @ApiResponse(responseCode = "403", description = "Caller is neither the owner nor an ADMIN",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    @ApiResponse(responseCode = "404", description = "No account with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = ACCOUNT_NOT_FOUND)))
    @ApiResponse(responseCode = "409", description = "Email already used by another account",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = EMAIL_ALREADY_EXISTS)))
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
    @Operation(summary = "Change your password",
            description = "Owner only (not even ADMIN). The current password must match and the new one must differ "
                    + "and follow the password rule.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = JSON, examples = @ExampleObject(value = PASSWORD_CHANGE_REQUEST))))
    @ApiResponse(responseCode = "200", description = "Password updated",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = PASSWORD_UPDATED)))
    @ApiResponse(responseCode = "400", description = "Validation failed, current password wrong, or new password unchanged",
            content = @Content(mediaType = JSON, examples = {
                    @ExampleObject(name = "wrong current password", value = WRONG_PASSWORD),
                    @ExampleObject(name = "same password", value = SAME_PASSWORD),
                    @ExampleObject(name = "weak password", value = VALIDATION_ERROR)}))
    @ApiResponse(responseCode = "403", description = "Caller is not the owner of this account",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    @ApiResponse(responseCode = "404", description = "No account with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = ACCOUNT_NOT_FOUND)))
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
    @Operation(summary = "Activate or suspend an account", description = "ADMIN only. Suspended accounts can't log in.")
    @ApiResponse(responseCode = "200", description = "Status updated",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = AccountResponse.class),
                    examples = @ExampleObject(value = ACCOUNT_RESPONSE)))
    @ApiResponse(responseCode = "400", description = "Status must be ACTIVE or SUSPENDED",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = INVALID_STATUS)))
    @ApiResponse(responseCode = "403", description = "Caller does not have the ADMIN authority",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    @ApiResponse(responseCode = "404", description = "No account with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = ACCOUNT_NOT_FOUND)))
    public ResponseEntity<?> setStatus(@PathVariable String id,
                                       @Parameter(description = "ACTIVE or SUSPENDED", example = "SUSPENDED")
                                       @RequestParam String status) {
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
    @Operation(summary = "Change an account's role", description = "ADMIN only. Any role, including ADMIN, may be assigned.")
    @ApiResponse(responseCode = "200", description = "Role updated",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = AccountResponse.class),
                    examples = @ExampleObject(value = ACCOUNT_RESPONSE)))
    @ApiResponse(responseCode = "400", description = "Role must be PASSENGER, DRIVER or ADMIN",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = INVALID_ROLE)))
    @ApiResponse(responseCode = "403", description = "Caller does not have the ADMIN authority",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    @ApiResponse(responseCode = "404", description = "No account with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = ACCOUNT_NOT_FOUND)))
    public ResponseEntity<?> setRole(@PathVariable String id,
                                     @Parameter(description = "PASSENGER, DRIVER or ADMIN", example = "DRIVER")
                                     @RequestParam String role) {
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
