package com.bankplatform.transaction.seed;

import com.bankplatform.common.event.CurrencyCode;
import com.bankplatform.common.security.Role;
import com.bankplatform.transaction.account.Account;
import com.bankplatform.transaction.account.AccountRepository;
import com.bankplatform.transaction.account.IbanGenerator;
import com.bankplatform.transaction.user.User;
import com.bankplatform.transaction.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

/**
 * Seeds demo users and accounts when {@code app.seed.enabled=true}: always in the {@code dev} profile,
 * and in {@code prod} only for the public demo ({@code DEMO_MODE=true}).
 * Account numbers are deterministic so the README examples work out of the box.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final SeedProperties seed;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(seed.adminPassword()) || !StringUtils.hasText(seed.customerPassword())) {
            throw new IllegalStateException(
                    "Demo seeding is enabled but SEED_ADMIN_PASSWORD / SEED_CUSTOMER_PASSWORD are not set");
        }
        if (userRepository.count() > 0) {
            log.info("Demo data already present - skipping seed");
            return;
        }
        user("admin", seed.adminPassword(), "Platform Administrator", Role.ADMIN);
        User alice = user("alice", seed.customerPassword(), "Alice Aliyeva", Role.CUSTOMER);
        User bob = user("bob", seed.customerPassword(), "Bob Mammadov", Role.CUSTOMER);

        List.of(
                account(alice, "00000000000000000001", CurrencyCode.AZN, "5000.00"),
                account(alice, "00000000000000000002", CurrencyCode.USD, "2500.00"),
                account(bob, "00000000000000000003", CurrencyCode.AZN, "1200.00"),
                account(bob, "00000000000000000004", CurrencyCode.EUR, "800.00")
        ).forEach(a -> log.info("Seeded account {} ({}) balance {}", a.getNumber(), a.getCurrency(), a.getBalance()));
        log.info("Demo data seeded: users admin, alice, bob");
    }

    private User user(String username, String password, String fullName, Role role) {
        return userRepository.save(new User(username, passwordEncoder.encode(password), fullName, role, clock.instant()));
    }

    private Account account(User owner, String accountPart, CurrencyCode currency, String balance) {
        return accountRepository.save(new Account(IbanGenerator.build(accountPart), owner.getId(), currency,
                new BigDecimal(balance), clock.instant()));
    }

    @ConfigurationProperties("app.seed")
    public record SeedProperties(String adminPassword, String customerPassword) {
    }
}
