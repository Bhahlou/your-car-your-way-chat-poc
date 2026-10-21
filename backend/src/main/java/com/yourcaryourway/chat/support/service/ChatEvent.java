package com.yourcaryourway.chat.support.service;

/**
 * Event broadcast on {@code /topic/conversations.{id}} to all participants of the conversation.
 * Published by the service as a Spring application event, then relayed over WebSocket only after the
 * transaction commits: a displayed message is therefore always a saved message (architecture §7.4).
 *
 * @param type         MESSAGE, TYPING, CLAIMED or CLOSED
 * @param message      set for MESSAGE
 * @param conversation set for CLAIMED and CLOSED
 * @param actorId      author of the action (for TYPING: who is typing)
 * @param typing       set for TYPING: the participant is typing (true) or has stopped (false)
 */
public record ChatEvent(
    Type type,
    long conversationId,
    MessageView message,
    ConversationView conversation,
    Long actorId,
    String actorName,
    Boolean typing) {

    public enum Type { MESSAGE, TYPING, CLAIMED, CLOSED }
}
