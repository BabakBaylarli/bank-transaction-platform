package com.bankplatform.transaction.auth.dto;

import com.bankplatform.common.security.Role;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String username, String fullName, Role role, Instant createdAt) {
}
