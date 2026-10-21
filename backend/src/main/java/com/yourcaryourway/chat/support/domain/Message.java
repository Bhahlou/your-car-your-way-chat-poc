package com.yourcaryourway.chat.support.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Message of a conversation. Plain text only: never interpreted as HTML. */
@Entity
@Table(name = "message")
public class Message {

    public static final int MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private Long conversationId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Column(nullable = false, updatable = false)
    private String content;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt = Instant.now();

    protected Message() {
    }

    public Message(Long conversationId, Long authorId, String content) {
        this.conversationId = conversationId;
        this.authorId = authorId;
        this.content = content;
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public Long getAuthorId() { return authorId; }
    public String getContent() { return content; }
    public Instant getSentAt() { return sentAt; }
}
