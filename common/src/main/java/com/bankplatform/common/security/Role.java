package com.bankplatform.common.security;

public enum Role {
    ADMIN, CUSTOMER;

    public String authority() {
        return "ROLE_" + name();
    }
}
