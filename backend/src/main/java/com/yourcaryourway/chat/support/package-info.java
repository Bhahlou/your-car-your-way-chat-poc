/**
 * Support module (PoC): chat between a customer and a customer service agent (epic 8 of the requirements).
 * <p>
 * Layered organization, identical for all modules:
 * <ul>
 *   <li>{@code web}: REST and STOMP controllers, no business rule;</li>
 *   <li>{@code service}: business rules (claiming, access rights, message validation);</li>
 *   <li>{@code repository}: PostgreSQL persistence (Spring Data JPA);</li>
 *   <li>{@code domain}: JPA entities;</li>
 *   <li>{@code config}: WebSocket / broker configuration.</li>
 * </ul>
 * This module depends on accounts only through {@link com.yourcaryourway.chat.accounts.AccountService}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Support", allowedDependencies = "accounts")
package com.yourcaryourway.chat.support;
