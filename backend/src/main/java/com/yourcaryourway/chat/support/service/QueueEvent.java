package com.yourcaryourway.chat.support.service;

/**
 * Event broadcast on {@code /topic/support.queue} to agents: a conversation enters the waiting queue
 * (WAITING), or leaves it because it was claimed (CLAIMED) or closed (CLOSED).
 */
public record QueueEvent(Type type, ConversationView conversation) {

    public enum Type { WAITING, CLAIMED, CLOSED }
}
