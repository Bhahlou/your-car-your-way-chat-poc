package com.yourcaryourway.chat.accounts.domain;

/** Matches the PostgreSQL type {@code account_status}. */
public enum AccountStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    ANONYMIZED
}
