package com.yourcaryourway.chat.support.service;

import com.yourcaryourway.chat.accounts.UserRole;
import org.springframework.security.core.Authentication;

/** Authenticated user performing an action (id + role), extracted from the JWT. Services only handle this type. */
public record Actor(long userId, UserRole role) {

    /** The JWT {@code subject} is the account id; the {@code ROLE_*} authority carries the role. */
    public static Actor from(Authentication authentication) {
        boolean agent = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_AGENT"));
        return new Actor(Long.parseLong(authentication.getName()), agent ? UserRole.AGENT : UserRole.CLIENT);
    }

    public boolean isAgent() {
        return role == UserRole.AGENT;
    }
}
