package com.yourcaryourway.chat.accounts.config;

import com.yourcaryourway.chat.accounts.UserRole;
import com.yourcaryourway.chat.accounts.domain.AppUser;
import com.yourcaryourway.chat.accounts.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates demo accounts at startup when {@code app.seed-demo-users=true} (docker compose and local dev).
 * Never enabled in production. Idempotent: does not recreate an existing account.
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-users", havingValue = "true")
class DemoDataSeeder implements ApplicationRunner {

    static final String DEMO_PASSWORD = "Password123!";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    DemoDataSeeder(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("client1@example.com", UserRole.CLIENT, "Camille", "Durand");
        seed("client2@example.com", UserRole.CLIENT, "Jonas", "Weber");
        seed("agent1@example.com", UserRole.AGENT, "Sophie", "Martin");
        seed("agent2@example.com", UserRole.AGENT, "Marc", "Lopez");
    }

    private void seed(String email, UserRole role, String firstName, String lastName) {
        if (users.findByEmailIgnoreCase(email).isEmpty()) {
            users.save(new AppUser(email, passwordEncoder.encode(DEMO_PASSWORD), role, firstName, lastName));
            log.info("Compte de démonstration créé : {} ({})", email, role);
        }
    }
}
