package com.bankplatform.transaction.account;

import com.bankplatform.common.security.AuthenticatedUser;
import com.bankplatform.transaction.account.dto.AccountResponse;
import com.bankplatform.transaction.account.dto.BalanceResponse;
import com.bankplatform.transaction.account.dto.CreateAccountRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Accounts")
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "Open a new account",
            description = "`initialDeposit` (max 10,000) simulates a cash deposit so the demo has money to move.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse open(@AuthenticationPrincipal AuthenticatedUser user,
                                @Valid @RequestBody CreateAccountRequest request) {
        return accountService.open(user, request);
    }

    @Operation(summary = "List my accounts")
    @GetMapping
    public List<AccountResponse> myAccounts(@AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.myAccounts(user);
    }

    @Operation(summary = "Current balance of an account (owner or ADMIN)")
    @GetMapping("/{number}/balance")
    public BalanceResponse balance(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String number) {
        return accountService.balance(user, number);
    }
}
