package com.yourcaryourway.chat.accounts;

import com.yourcaryourway.chat.accounts.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Only entry point of the other modules to accounts: they never access the repository
 * (rule verified by {@code ModularityTests}).
 */
@Service
public class AccountService {

    private final UserRepository users;

    AccountService(UserRepository users) {
        this.users = users;
    }

    /** Display names ("First N.") keyed by id, to enrich messages and conversations. */
    @Transactional(readOnly = true)
    public Map<Long, String> displayNames(Collection<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        users.findAllById(userIds).forEach(u -> names.put(u.getId(), u.displayName()));
        return names;
    }
}
