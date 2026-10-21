package com.yourcaryourway.chat.support.web;

import com.yourcaryourway.chat.support.service.ChatEvent;
import com.yourcaryourway.chat.support.service.QueueEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridge between the service's application events and the WebSocket channel.
 * <p>
 * {@code AFTER_COMMIT} guarantees that we never broadcast a message whose
 * saving failed.
 * {@code fallbackExecution} covers events published outside a transaction (e.g.
 * typing indicator).
 */
@Component
class ChatBroadcaster {

    static final String CONVERSATION_TOPIC = "/topic/conversations.";
    static final String QUEUE_TOPIC = "/topic/support.queue";

    private final SimpMessagingTemplate template;

    ChatBroadcaster(SimpMessagingTemplate template) {
        this.template = template;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onChatEvent(ChatEvent event) {
        template.convertAndSend(CONVERSATION_TOPIC + event.conversationId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onQueueEvent(QueueEvent event) {
        template.convertAndSend(QUEUE_TOPIC, event);
    }
}
