package com.yourcaryourway.chat.support.domain;

/**
 * Conversation lifecycle: WAITING (waiting for an agent) -> OPEN (claimed) -> CLOSED.
 * Matches the PostgreSQL type {@code conversation_status}.
 */
public enum ConversationStatus {
    WAITING,
    OPEN,
    CLOSED
}
