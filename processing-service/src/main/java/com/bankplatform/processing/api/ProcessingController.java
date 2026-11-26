package com.bankplatform.processing.api;

import com.bankplatform.common.error.NotFoundException;
import com.bankplatform.processing.fraud.FraudProperties;
import com.bankplatform.processing.ledger.ProcessedTransaction;
import com.bankplatform.processing.ledger.ProcessedTransactionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Processing (ADMIN)")
@RestController
@RequestMapping("/api/v1/processing")
@RequiredArgsConstructor
public class ProcessingController {

    private final FraudProperties fraudProperties;
    private final ProcessedTransactionRepository repository;
    private final ProcessingMapper mapper;

    @Operation(summary = "Active fraud rule limits")
    @GetMapping("/fraud-rules")
    public FraudProperties fraudRules() {
        return fraudProperties;
    }

    @Operation(summary = "Processing decision for a transaction")
    @GetMapping("/transactions/{id}")
    public ProcessedTransactionResponse decision(@PathVariable UUID id) {
        ProcessedTransaction processed = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Processed transaction", id));
        return mapper.toResponse(processed);
    }
}
