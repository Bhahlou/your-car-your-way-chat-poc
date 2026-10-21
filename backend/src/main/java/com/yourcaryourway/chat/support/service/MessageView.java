package com.yourcaryourway.chat.support.service;

import java.time.Instant;

/** Representation of a message exposed by the API and broadcast on the WebSocket channel. */
public record MessageView(long id, long conversationId, long authorId, String authorName, String content, Instant sentAt) {
}
