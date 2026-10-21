package com.yourcaryourway.chat.support.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Support exchange between a customer and an agent. References to accounts are plain ids:
 * the Support module does not handle the Accounts module's entities.
 */
@Entity
@Table(name = "conversation")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, updatable = false)
    private Long clientId;

    @Column(name = "reservation_id", updatable = false)
    private Long reservationId;

    @Column(name = "agent_id")
    private Long agentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "conversation_status")
    private ConversationStatus status = ConversationStatus.WAITING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    protected Conversation() {
    }

    public Conversation(Long clientId, Long reservationId) {
        this.clientId = clientId;
        this.reservationId = reservationId;
    }

    public Long getId() { return id; }
    public Long getClientId() { return clientId; }
    public Long getReservationId() { return reservationId; }
    public Long getAgentId() { return agentId; }
    public ConversationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClosedAt() { return closedAt; }
}
