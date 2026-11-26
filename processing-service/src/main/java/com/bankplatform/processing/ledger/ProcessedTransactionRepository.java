package com.bankplatform.processing.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface ProcessedTransactionRepository extends JpaRepository<ProcessedTransaction, UUID> {

    long countBySourceAccountAndProcessedAtAfter(String sourceAccount, Instant after);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM ProcessedTransaction p
            WHERE p.sourceAccount = :account
              AND p.status = com.bankplatform.common.event.TransactionStatus.COMPLETED
              AND p.processedAt >= :since
            """)
    BigDecimal sumCompletedOutgoingSince(@Param("account") String account, @Param("since") Instant since);
}
