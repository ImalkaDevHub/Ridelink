package com.ridelink.account_service.service;

import com.ridelink.account_service.dto.AccountResponse;
import com.ridelink.account_service.dto.AuthResponse;
import com.ridelink.account_service.dto.LoginRequest;
import com.ridelink.account_service.dto.ProfileUpdateRequest;
import com.ridelink.account_service.dto.RegisterRequest;
import com.ridelink.account_service.model.Account;
import com.ridelink.account_service.model.Role;
import com.ridelink.account_service.model.Status;
import com.ridelink.account_service.repository.AccountRepository;
import com.ridelink.account_service.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest request) {
        if (accountRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("An account with this email already exists.");
        }

        Account account = new Account();
        account.setName(request.getName());
        account.setEmail(request.getEmail());
        account.setPassword(passwordEncoder.encode(request.getPassword()));
        account.setRole(resolveRegistrationRole(request.getRole()));
        account.setStatus(Status.ACTIVE);

        Account saved = accountRepository.save(account);
        String token = jwtUtil.generateToken(saved);

        return new AuthResponse(token, saved.getId(), saved.getName(), saved.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password."));

        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        if (account.getStatus() == Status.SUSPENDED) {
            throw new AccountSuspendedException("This account has been suspended.");
        }

        String token = jwtUtil.generateToken(account);

        return new AuthResponse(token, account.getId(), account.getName(), account.getRole().name());
    }

    public AccountResponse getProfile(Long id) {
        return toResponse(findOrThrow(id));
    }

    public AccountResponse updateProfile(Long id, ProfileUpdateRequest request) {
        Account account = findOrThrow(id);

        accountRepository.findByEmail(request.getEmail())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new EmailAlreadyExistsException("An account with this email already exists.");
                });

        account.setName(request.getName());
        account.setEmail(request.getEmail());

        return toResponse(accountRepository.save(account));
    }

    public AccountResponse setStatus(Long id, String status) {
        Account account = findOrThrow(id);

        Status newStatus;
        try {
            newStatus = Status.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidStatusException("Status must be one of ACTIVE, SUSPENDED.");
        }

        account.setStatus(newStatus);
        return toResponse(accountRepository.save(account));
    }

    private Account findOrThrow(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with id: " + id));
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getEmail(),
                account.getRole().name(),
                account.getStatus().name());
    }

    // Self-registration may only create PASSENGER or DRIVER accounts.
    // Blank/omitted role defaults to PASSENGER.
    private Role resolveRegistrationRole(String requestedRole) {
        if (requestedRole == null || requestedRole.isBlank()) {
            return Role.PASSENGER;
        }

        String normalized = requestedRole.trim().toUpperCase();
        if (normalized.equals(Role.PASSENGER.name()) || normalized.equals(Role.DRIVER.name())) {
            return Role.valueOf(normalized);
        }

        throw new InvalidRoleException("Role must be one of PASSENGER, DRIVER.");
    }

    public static class EmailAlreadyExistsException extends RuntimeException {
        public EmailAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String message) {
            super(message);
        }
    }

    public static class AccountSuspendedException extends RuntimeException {
        public AccountSuspendedException(String message) {
            super(message);
        }
    }

    public static class AccountNotFoundException extends RuntimeException {
        public AccountNotFoundException(String message) {
            super(message);
        }
    }

    public static class InvalidRoleException extends RuntimeException {
        public InvalidRoleException(String message) {
            super(message);
        }
    }

    public static class InvalidStatusException extends RuntimeException {
        public InvalidStatusException(String message) {
            super(message);
        }
    }
}
