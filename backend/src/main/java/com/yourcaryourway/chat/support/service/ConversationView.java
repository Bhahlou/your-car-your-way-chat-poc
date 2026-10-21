package com.yourcaryourway.chat.support.service;

import com.yourcaryourway.chat.support.domain.ConversationStatus;

import java.time.Instant;

/** Representation of a conversation exposed by the API (never the JPA entity). */
public record ConversationView(
    long id,
    ConversationStatus status,
    long clientId,
    String clientName,
    Long agentId,
    String agentName,
    Long reservationId,
    Instant createdAt,
    Instant closedAt) {
}
