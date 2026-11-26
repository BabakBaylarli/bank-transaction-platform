package com.bankplatform.transaction.account;

import com.bankplatform.common.error.NotFoundException;
import com.bankplatform.common.security.AuthenticatedUser;
import com.bankplatform.transaction.account.dto.AccountResponse;
import com.bankplatform.transaction.account.dto.BalanceResponse;
import com.bankplatform.transaction.account.dto.CreateAccountRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final int MAX_NUMBER_ATTEMPTS = 5;

    private final AccountRepository accountRepository;
    private final IbanGenerator ibanGenerator;
    private final AccountMapper accountMapper;
    private final Clock clock;

    @Transactional
    public AccountResponse open(AuthenticatedUser user, CreateAccountRequest request) {
        BigDecimal opening = request.initialDeposit() != null ? request.initialDeposit() : BigDecimal.ZERO;
        Account account = accountRepository.save(
                new Account(uniqueNumber(), user.id(), request.currency(), opening, clock.instant()));
        log.info("Opened {} account {} for user {}", account.getCurrency(), account.getNumber(), user.id());
        return accountMapper.toResponse(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> myAccounts(AuthenticatedUser user) {
        return accountRepository.findByOwnerIdOrderByCreatedAt(user.id()).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(AuthenticatedUser user, String number) {
        Account account = accountRepository.findByNumber(number)
                // customers get 404 for foreign accounts: we do not reveal that the account exists
                .filter(a -> user.isAdmin() || a.isOwnedBy(user.id()))
                .orElseThrow(() -> new NotFoundException("Account", number));
        return new BalanceResponse(account.getNumber(), account.getBalance(), account.getCurrency(), clock.instant());
    }

    private String uniqueNumber() {
        for (int i = 0; i < MAX_NUMBER_ATTEMPTS; i++) {
            String candidate = ibanGenerator.generate();
            if (!accountRepository.existsByNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique account number");
    }
}
