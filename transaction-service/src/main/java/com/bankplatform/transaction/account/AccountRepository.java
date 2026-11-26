package com.bankplatform.transaction.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByNumber(String number);

    List<Account> findByOwnerIdOrderByCreatedAt(UUID ownerId);

    boolean existsByNumber(String number);
}
