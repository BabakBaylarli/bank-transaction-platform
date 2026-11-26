package com.bankplatform.transaction.transfer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByInitiatorIdAndIdempotencyKey(UUID initiatorId, String idempotencyKey);

    Page<Transaction> findByInitiatorId(UUID initiatorId, Pageable pageable);
}
