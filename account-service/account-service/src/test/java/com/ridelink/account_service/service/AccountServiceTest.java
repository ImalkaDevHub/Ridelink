package com.ridelink.account_service.service;

import com.ridelink.account_service.dto.AuthResponse;
import com.ridelink.account_service.dto.LoginRequest;
import com.ridelink.account_service.dto.PasswordChangeRequest;
import com.ridelink.account_service.dto.RegisterRequest;
import com.ridelink.account_service.model.Account;
import com.ridelink.account_service.model.Role;
import com.ridelink.account_service.model.Status;
import com.ridelink.account_service.repository.AccountRepository;
import com.ridelink.account_service.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit tests for AccountService with the repository, encoder and JWT util
// mocked - no MongoDB or Spring context needed.
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AccountService accountService;

    private RegisterRequest registerRequest(String role) {
        RegisterRequest request = new RegisterRequest();
        request.setName("Nimal Perera");
        request.setEmail("nimal@example.com");
        request.setPassword("secret123");
        request.setRole(role);
        return request;
    }

    private LoginRequest loginRequest(String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail("nimal@example.com");
        request.setPassword(password);
        return request;
    }

    private Account storedAccount(Status status) {
        Account account = new Account();
        account.setId("acc-1");
        account.setName("Nimal Perera");
        account.setEmail("nimal@example.com");
        account.setPassword("$hash");
        account.setRole(Role.PASSENGER);
        account.setStatus(status);
        return account;
    }

    private PasswordChangeRequest passwordChange(String current, String next) {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        return request;
    }

    // --- register ---------------------------------------------------------

    @Test
    void register_withNewEmail_savesHashedPasswordAndReturnsToken() {
        when(accountRepository.findByEmail("nimal@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("$hash");
        when(accountRepository.save(any())).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            saved.setId("acc-1");
            return saved;
        });
        when(jwtUtil.generateToken(any())).thenReturn("jwt-token");

        AuthResponse response = accountService.register(registerRequest("driver"));

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$hash");
        assertThat(captor.getValue().getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getId()).isEqualTo("acc-1");
        assertThat(response.getRole()).isEqualTo("DRIVER");
    }

    @Test
    void register_withBlankRole_defaultsToPassenger() {
        when(accountRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = accountService.register(registerRequest(" "));

        assertThat(response.getRole()).isEqualTo("PASSENGER");
    }

    @Test
    void register_withExistingEmail_throwsEmailAlreadyExists() {
        when(accountRepository.findByEmail("nimal@example.com")).thenReturn(Optional.of(storedAccount(Status.ACTIVE)));

        assertThatThrownBy(() -> accountService.register(registerRequest("PASSENGER")))
                .isInstanceOf(AccountService.EmailAlreadyExistsException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void register_whenUniqueIndexRejectsConcurrentDuplicate_throwsEmailAlreadyExists() {
        // Pre-check passed, but another request inserted the same email first.
        when(accountRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(accountRepository.save(any())).thenThrow(new DuplicateKeyException("E11000 duplicate key"));

        assertThatThrownBy(() -> accountService.register(registerRequest("PASSENGER")))
                .isInstanceOf(AccountService.EmailAlreadyExistsException.class);
    }

    @Test
    void register_withAdminRole_throwsInvalidRole() {
        when(accountRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.register(registerRequest("ADMIN")))
                .isInstanceOf(AccountService.InvalidRoleException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void register_withUnknownRole_throwsInvalidRole() {
        when(accountRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.register(registerRequest("PILOT")))
                .isInstanceOf(AccountService.InvalidRoleException.class);
    }

    // --- login ------------------------------------------------------------

    @Test
    void login_withCorrectPassword_returnsToken() {
        Account account = storedAccount(Status.ACTIVE);
        when(accountRepository.findByEmail("nimal@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret123", "$hash")).thenReturn(true);
        when(jwtUtil.generateToken(account)).thenReturn("jwt-token");

        AuthResponse response = accountService.login(loginRequest("secret123"));

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getId()).isEqualTo("acc-1");
    }

    @Test
    void login_withWrongPassword_throwsInvalidCredentials() {
        when(accountRepository.findByEmail("nimal@example.com")).thenReturn(Optional.of(storedAccount(Status.ACTIVE)));
        when(passwordEncoder.matches("wrong123", "$hash")).thenReturn(false);

        assertThatThrownBy(() -> accountService.login(loginRequest("wrong123")))
                .isInstanceOf(AccountService.InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");
    }

    @Test
    void login_withUnknownEmail_throwsTheSameGenericError() {
        when(accountRepository.findByEmail("nimal@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.login(loginRequest("secret123")))
                .isInstanceOf(AccountService.InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");
    }

    @Test
    void login_whenSuspended_throwsAccountSuspendedAndIssuesNoToken() {
        when(accountRepository.findByEmail("nimal@example.com")).thenReturn(Optional.of(storedAccount(Status.SUSPENDED)));
        when(passwordEncoder.matches("secret123", "$hash")).thenReturn(true);

        assertThatThrownBy(() -> accountService.login(loginRequest("secret123")))
                .isInstanceOf(AccountService.AccountSuspendedException.class);

        verify(jwtUtil, never()).generateToken(any());
    }

    // --- changePassword ---------------------------------------------------

    @Test
    void changePassword_withCorrectCurrentAndNewPassword_savesNewHash() {
        Account account = storedAccount(Status.ACTIVE);
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret123", "$hash")).thenReturn(true);
        when(passwordEncoder.matches("newpass456", "$hash")).thenReturn(false);
        when(passwordEncoder.encode("newpass456")).thenReturn("$newhash");

        accountService.changePassword("acc-1", passwordChange("secret123", "newpass456"));

        assertThat(account.getPassword()).isEqualTo("$newhash");
        verify(accountRepository).save(account);
    }

    @Test
    void changePassword_withWrongCurrentPassword_throwsWrongPassword() {
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(storedAccount(Status.ACTIVE)));
        when(passwordEncoder.matches("nope1234", "$hash")).thenReturn(false);

        assertThatThrownBy(() -> accountService.changePassword("acc-1", passwordChange("nope1234", "newpass456")))
                .isInstanceOf(AccountService.WrongPasswordException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void changePassword_withSamePasswordAsCurrent_throwsSamePassword() {
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(storedAccount(Status.ACTIVE)));
        when(passwordEncoder.matches("secret123", "$hash")).thenReturn(true);

        assertThatThrownBy(() -> accountService.changePassword("acc-1", passwordChange("secret123", "secret123")))
                .isInstanceOf(AccountService.SamePasswordException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void changePassword_forUnknownAccount_throwsAccountNotFound() {
        when(accountRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.changePassword("missing", passwordChange("secret123", "newpass456")))
                .isInstanceOf(AccountService.AccountNotFoundException.class);
    }

    // --- admin operations -------------------------------------------------

    @Test
    void setRole_withValidRole_updatesRole() {
        Account account = storedAccount(Status.ACTIVE);
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        assertThat(accountService.setRole("acc-1", "admin").getRole()).isEqualTo("ADMIN");
    }

    @Test
    void setRole_withUnknownRole_throwsInvalidRole() {
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(storedAccount(Status.ACTIVE)));

        assertThatThrownBy(() -> accountService.setRole("acc-1", "PILOT"))
                .isInstanceOf(AccountService.InvalidRoleException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void listAccounts_clampsPageSizeToTheMaximum() {
        when(accountRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenAnswer(invocation -> new org.springframework.data.domain.PageImpl<Account>(
                        java.util.List.of(storedAccount(Status.ACTIVE)), invocation.getArgument(0), 1));

        var page = accountService.listAccounts(-1, 500);

        assertThat(page.getPage()).isZero();
        assertThat(page.getSize()).isEqualTo(AccountService.MAX_PAGE_SIZE);
        assertThat(page.getContent()).extracting("email").containsExactly("nimal@example.com");
    }
}
