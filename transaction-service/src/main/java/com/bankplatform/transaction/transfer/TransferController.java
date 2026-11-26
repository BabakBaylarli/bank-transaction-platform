package com.bankplatform.transaction.transfer;

import com.bankplatform.common.security.AuthenticatedUser;
import com.bankplatform.common.web.PageResponse;
import com.bankplatform.transaction.transfer.dto.CreateTransferRequest;
import com.bankplatform.transaction.transfer.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@Tag(name = "Transactions")
@Validated
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransferController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final TransferService transferService;

    @Operation(summary = "Create a transfer",
            description = "Accepted asynchronously: the transfer is stored as PENDING and processed via Kafka. "
                    + "Retrying with the same Idempotency-Key returns the original result.")
    @ApiResponse(responseCode = "202", description = "Transfer accepted")
    @ApiResponse(responseCode = "200", description = "Replay of an earlier request with the same key")
    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Client-generated unique key, e.g. a UUID")
            @RequestHeader(IDEMPOTENCY_KEY) @Pattern(regexp = "[A-Za-z0-9_-]{8,100}",
                    message = "must be 8-100 characters of letters, digits, '-' or '_'") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request) {
        TransferResult result = transferService.createTransfer(user, idempotencyKey, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(result.transaction().id()).toUri();
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .location(location)
                .header(IDEMPOTENT_REPLAYED, String.valueOf(result.replayed()))
                .body(result.transaction());
    }

    @Operation(summary = "Get a transaction (initiator or ADMIN)")
    @GetMapping("/{id}")
    public TransactionResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return transferService.get(user, id);
    }

    @Operation(summary = "List transactions I initiated")
    @GetMapping
    public PageResponse<TransactionResponse> mine(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return transferService.mine(user, pageable);
    }
}
