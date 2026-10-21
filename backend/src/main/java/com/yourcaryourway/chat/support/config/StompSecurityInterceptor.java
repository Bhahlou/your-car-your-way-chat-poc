package com.yourcaryourway.chat.support.config;

import com.yourcaryourway.chat.support.service.Actor;
import com.yourcaryourway.chat.support.service.ConversationService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebSocket channel security (architecture §5.5):
 * <ul>
 *   <li>CONNECT: the JWT in the {@code Authorization} header is verified, otherwise the connection is refused;</li>
 *   <li>SUBSCRIBE: a customer can only subscribe to their own conversations, an agent only to those they have claimed
 *       and to the waiting queue;</li>
 *   <li>SEND: only to {@code /app/...} (never straight to a topic, which would allow spoofing a message).</li>
 * </ul>
 * Membership of the conversation is re-checked on every message by {@link ConversationService}.
 */
@Component
class StompSecurityInterceptor implements ChannelInterceptor {

    private static final Pattern CONVERSATION_TOPIC = Pattern.compile("^/topic/conversations\\.(\\d+)$");
    private static final String QUEUE_TOPIC = "/topic/support.queue";
    private static final String ERRORS_QUEUE = "/user/queue/errors";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtConverter;
    private final ConversationService conversations;

    StompSecurityInterceptor(JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtConverter, ConversationService conversations) {
        this.jwtDecoder = jwtDecoder;
        this.jwtConverter = jwtConverter;
        this.conversations = conversations;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        StompCommand command = accessor == null ? null : accessor.getCommand();
        if (command == null) {
            return message; // service frame (heartbeat...)
        }
        switch (command) {
            case CONNECT, STOMP -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> authorizeSend(accessor);
            default -> {
                // UNSUBSCRIBE, DISCONNECT, ACK...: nothing to check
            }
        }
        return message;
    }

    private Authentication authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BadCredentialsException("Jeton manquant");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring("Bearer ".length()));
            return jwtConverter.convert(jwt);
        } catch (JwtException e) {
            throw new BadCredentialsException("Jeton invalide ou expiré", e);
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Authentication user = requireUser(accessor);
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new AccessDeniedException("Destination manquante");
        }
        Actor actor = Actor.from(user);
        Matcher conversation = CONVERSATION_TOPIC.matcher(destination);
        if (conversation.matches()) {
            conversations.requireAccess(actor, Long.parseLong(conversation.group(1)));
        } else if (destination.equals(QUEUE_TOPIC)) {
            if (!actor.isAgent()) {
                throw new AccessDeniedException("Réservé aux conseillers");
            }
        } else if (!destination.equals(ERRORS_QUEUE)) {
            throw new AccessDeniedException("Destination non autorisée");
        }
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        requireUser(accessor);
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/app/")) {
            throw new AccessDeniedException("Envoi autorisé uniquement vers /app/...");
        }
    }

    private Authentication requireUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof Authentication authentication && authentication.isAuthenticated()) {
            return authentication;
        }
        throw new AccessDeniedException("Connexion non authentifiée");
    }
}
