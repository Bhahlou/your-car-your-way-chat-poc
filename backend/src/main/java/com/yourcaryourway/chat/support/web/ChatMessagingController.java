package com.yourcaryourway.chat.support.web;

import com.yourcaryourway.chat.support.service.Actor;
import com.yourcaryourway.chat.support.service.ChatException;
import com.yourcaryourway.chat.support.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP entry points: the client sends to {@code /app/conversations/{id}/...}. The controller broadcasts nothing itself:
 * the service saves, then {@link ChatBroadcaster} publishes on {@code /topic/conversations.{id}} after the commit.
 */
@Controller
class ChatMessagingController {

    private static final Logger log = LoggerFactory.getLogger(ChatMessagingController.class);

    private final ConversationService service;

    ChatMessagingController(ConversationService service) {
        this.service = service;
    }

    record SendMessage(String content) {
    }

    record Typing(boolean typing) {
    }

    /** Error sent only to the sender, on {@code /user/queue/errors}. */
    record ErrorView(String code, String message) {
    }

    @MessageMapping("/conversations/{id}/messages")
    void send(@DestinationVariable long id, @Payload SendMessage payload, Principal principal) {
        service.postMessage(Actor.from((Authentication) principal), id, payload.content());
    }

    @MessageMapping("/conversations/{id}/typing")
    void typing(@DestinationVariable long id, @Payload Typing payload, Principal principal) {
        service.typing(Actor.from((Authentication) principal), id, payload.typing());
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    ErrorView onError(Exception e) {
        if (e instanceof ChatException chat) {
            return new ErrorView(chat.status().name(), chat.getMessage());
        }
        log.error("Erreur inattendue dans un message STOMP", e);
        return new ErrorView("INTERNAL_ERROR", "Une erreur est survenue, veuillez réessayer");
    }
}
