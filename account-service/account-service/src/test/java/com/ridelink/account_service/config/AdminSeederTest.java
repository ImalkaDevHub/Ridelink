package com.ridelink.account_service.config;

import com.ridelink.account_service.model.Account;
import com.ridelink.account_service.model.Role;
import com.ridelink.account_service.model.Status;
import com.ridelink.account_service.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminSeeder seeder(boolean enabled) {
        return new AdminSeeder(accountRepository, passwordEncoder, enabled,
                "admin@ridelink.local", "DevAdmin123", "RideLink Admin");
    }

    @Test
    void createsActiveAdminWhenNoneExists() {
        when(accountRepository.findByEmail("admin@ridelink.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("DevAdmin123")).thenReturn("$hash");

        seeder(true).run();

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(captor.getValue().getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(captor.getValue().getPassword()).isEqualTo("$hash");
    }

    @Test
    void doesNothingWhenTheEmailIsAlreadyTaken() {
        when(accountRepository.findByEmail("admin@ridelink.local")).thenReturn(Optional.of(new Account()));

        seeder(true).run();

        verify(accountRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenDisabled() {
        seeder(false).run();

        verifyNoInteractions(accountRepository, passwordEncoder);
    }
}
