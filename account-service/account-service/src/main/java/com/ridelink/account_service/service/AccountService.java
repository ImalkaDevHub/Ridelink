package com.ridelink.account_service.service;

import com.ridelink.account_service.dto.AccountResponse;
import com.ridelink.account_service.dto.AuthResponse;
import com.ridelink.account_service.dto.LoginRequest;
import com.ridelink.account_service.dto.PagedResponse;
import com.ridelink.account_service.dto.PasswordChangeRequest;
import com.ridelink.account_service.dto.ProfileUpdateRequest;
import com.ridelink.account_service.dto.RegisterRequest;
import com.ridelink.account_service.model.Account;
import com.ridelink.account_service.model.Role;
import com.ridelink.account_service.model.Status;
import com.ridelink.account_service.repository.AccountRepository;
import com.ridelink.account_service.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    public static final int MAX_PAGE_SIZE = 100;

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

        Account saved = saveUniqueEmail(account);
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

    public AccountResponse getProfile(String id) {
        return toResponse(findOrThrow(id));
    }

    public AccountResponse updateProfile(String id, ProfileUpdateRequest request) {
        Account account = findOrThrow(id);

        accountRepository.findByEmail(request.getEmail())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new EmailAlreadyExistsException("An account with this email already exists.");
                });

        account.setName(request.getName());
        account.setEmail(request.getEmail());

        return toResponse(saveUniqueEmail(account));
    }

    public void changePassword(String id, PasswordChangeRequest request) {
        Account account = findOrThrow(id);

        if (!passwordEncoder.matches(request.getCurrentPassword(), account.getPassword())) {
            throw new WrongPasswordException("Current password is incorrect.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), account.getPassword())) {
            throw new SamePasswordException("New password must be different from the current password.");
        }

        account.setPassword(passwordEncoder.encode(request.getNewPassword()));
        accountRepository.save(account);
    }

    public PagedResponse<AccountResponse> listAccounts(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<Account> result = accountRepository.findAll(
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "id")));

        return new PagedResponse<>(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    public AccountResponse setStatus(String id, String status) {
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

    // Admin-only. Unlike self-registration, any role (including ADMIN) may
    // be assigned here.
    public AccountResponse setRole(String id, String role) {
        Account account = findOrThrow(id);

        Role newRole;
        try {
            newRole = Role.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRoleException("Role must be one of PASSENGER, DRIVER, ADMIN.");
        }

        account.setRole(newRole);
        return toResponse(accountRepository.save(account));
    }

    private Account findOrThrow(String id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with id: " + id));
    }

    // The findByEmail pre-check gives a clean error in the normal case; the
    // unique index on email catches two concurrent requests that both pass it.
    private Account saveUniqueEmail(Account account) {
        try {
            return accountRepository.save(account);
        } catch (DuplicateKeyException e) {
            throw new EmailAlreadyExistsException("An account with this email already exists.");
        }
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

    public static class WrongPasswordException extends RuntimeException {
        public WrongPasswordException(String message) {
            super(message);
        }
    }

    public static class SamePasswordException extends RuntimeException {
        public SamePasswordException(String message) {
            super(message);
        }
    }
}
