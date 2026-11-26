package com.bankplatform.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

/** The principal extracted from a validated JWT. Injected with {@code @AuthenticationPrincipal}. */
public record AuthenticatedUser(UUID id, String username, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }
}
