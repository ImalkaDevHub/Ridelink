package com.ridelink.account_service.config;

import com.ridelink.account_service.model.Account;
import com.ridelink.account_service.model.Role;
import com.ridelink.account_service.model.Status;
import com.ridelink.account_service.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Self-registration can never create an ADMIN, so one is seeded at startup
// from ADMIN_EMAIL / ADMIN_PASSWORD (dev defaults in application.properties)
// if no account with that email exists yet. An existing account with that
// email is left untouched, whatever its role.
//
// Constructor injection (rather than this project's usual field @Autowired)
// so it can be unit tested with plain mocks, without a Spring context.
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String email;
    private final String password;
    private final String name;

    public AdminSeeder(AccountRepository accountRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.admin.seed-enabled:true}") boolean enabled,
                       @Value("${app.admin.email}") String email,
                       @Value("${app.admin.password}") String password,
                       @Value("${app.admin.name:RideLink Admin}") String name) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }

        if (accountRepository.findByEmail(email).isPresent()) {
            log.info("Admin seed skipped: an account with email {} already exists.", email);
            return;
        }

        Account admin = new Account();
        admin.setName(name);
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setStatus(Status.ACTIVE);
        accountRepository.save(admin);

        log.info("Seeded ADMIN account {}.", email);
    }
}
