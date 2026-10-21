package com.yourcaryourway.chat.support.service;

import com.yourcaryourway.chat.accounts.AccountService;
import com.yourcaryourway.chat.support.domain.Conversation;
import com.yourcaryourway.chat.support.domain.ConversationStatus;
import com.yourcaryourway.chat.support.domain.Message;
import com.yourcaryourway.chat.support.repository.ConversationRepository;
import com.yourcaryourway.chat.support.repository.MessageRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Chat business rules (US-8.1 to US-8.5):
 * <ul>
 *   <li>a customer has only one unclosed conversation at a time;</li>
 *   <li>a conversation is claimed by a single agent, atomically;</li>
 *   <li>a customer only sees their own conversations, an agent only those they have claimed;</li>
 *   <li>a message is saved in the database before being broadcast.</li>
 * </ul>
 * Real-time notifications go through application events; this service knows nothing about WebSocket.
 */
@Service
@Transactional
public class ConversationService {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final AccountService accounts;
    private final ApplicationEventPublisher events;

    ConversationService(ConversationRepository conversations, MessageRepository messages,
                        AccountService accounts, ApplicationEventPublisher events) {
        this.conversations = conversations;
        this.messages = messages;
        this.accounts = accounts;
        this.events = events;
    }

    /** Result of opening: {@code created = false} if the customer already had an ongoing conversation. */
    public record OpenResult(ConversationView conversation, boolean created) {
    }

    // --- Customer ---------------------------------------------------------------------------------------------------

    /** US-8.1: opens a conversation (optionally linked to a reservation) or returns the one already ongoing. */
    public OpenResult open(Actor client, Long reservationId) {
        requireClient(client);
        Optional<Conversation> existing = conversations.findByClientIdAndStatusNot(client.userId(), ConversationStatus.CLOSED);
        if (existing.isPresent()) {
            return new OpenResult(toView(existing.get()), false);
        }
        Conversation created = conversations.saveAndFlush(new Conversation(client.userId(), reservationId));
        ConversationView view = toView(created);
        events.publishEvent(new QueueEvent(QueueEvent.Type.WAITING, view));
        return new OpenResult(view, true);
    }

    /** US-8.3: the customer's conversation history, newest first. */
    @Transactional(readOnly = true)
    public List<ConversationView> listForClient(Actor client) {
        requireClient(client);
        return toViews(conversations.findByClientIdOrderByCreatedAtDesc(client.userId()));
    }

    // --- Agent -----------------------------------------------------------------------------------------------

    /** US-8.4: waiting conversations, oldest first (arrival order). */
    @Transactional(readOnly = true)
    public List<ConversationView> listQueue(Actor agent) {
        requireAgent(agent);
        return toViews(conversations.findByStatusOrderByCreatedAtAsc(ConversationStatus.WAITING));
    }

    /** Ongoing conversations claimed by this agent. */
    @Transactional(readOnly = true)
    public List<ConversationView> listAssigned(Actor agent) {
        requireAgent(agent);
        return toViews(conversations.findByAgentIdAndStatusOrderByCreatedAtAsc(agent.userId(), ConversationStatus.OPEN));
    }

    /** US-8.4: atomic claim. Fails (409) if another agent claimed it just before. */
    public ConversationView claim(Actor agent, long conversationId) {
        requireAgent(agent);
        load(conversationId); // 404 if it does not exist
        if (conversations.claim(conversationId, agent.userId()) == 0) {
            throw ChatException.conflict("Cette conversation a déjà été prise en charge ou n'est plus en attente");
        }
        ConversationView view = toView(load(conversationId));
        events.publishEvent(new ChatEvent(ChatEvent.Type.CLAIMED, conversationId, null, view, agent.userId(), view.agentName(), null));
        events.publishEvent(new QueueEvent(QueueEvent.Type.CLAIMED, view));
        return view;
    }

    /** US-8.5: the agent closes once the request is handled; the customer is informed in the chat. */
    public ConversationView close(Actor agent, long conversationId) {
        requireAgent(agent);
        Conversation c = load(conversationId);
        requireParticipant(c, agent);
        if (conversations.close(conversationId, agent.userId(), Instant.now()) == 0) {
            throw ChatException.conflict("Cette conversation est déjà clôturée");
        }
        ConversationView view = toView(load(conversationId));
        events.publishEvent(new ChatEvent(ChatEvent.Type.CLOSED, conversationId, null, view, agent.userId(), view.agentName(), null));
        events.publishEvent(new QueueEvent(QueueEvent.Type.CLOSED, view));
        return view;
    }

    // --- Messages (customer and agent) --------------------------------------------------------------------------

    /** US-8.2: saves a message, then (after commit) broadcasts it to the participants. */
    public MessageView postMessage(Actor author, long conversationId, String rawContent) {
        Conversation c = load(conversationId);
        requireParticipant(c, author);
        if (c.getStatus() == ConversationStatus.CLOSED) {
            throw ChatException.conflict("La conversation est clôturée : ouvrez-en une nouvelle");
        }
        String content = normalize(rawContent);
        Message saved = messages.save(new Message(conversationId, author.userId(), content));
        MessageView view = new MessageView(saved.getId(), conversationId, author.userId(),
            accounts.displayNames(Set.of(author.userId())).getOrDefault(author.userId(), "?"),
            saved.getContent(), saved.getSentAt());
        events.publishEvent(new ChatEvent(ChatEvent.Type.MESSAGE, conversationId, view, null, author.userId(), view.authorName(), null));
        return view;
    }

    /** History, or catch-up after a disconnection if {@code afterId} is given (messages newer than the last one received). */
    @Transactional(readOnly = true)
    public List<MessageView> listMessages(Actor actor, long conversationId, Long afterId) {
        requireParticipant(load(conversationId), actor);
        List<Message> found = afterId == null
            ? messages.findByConversationIdOrderByIdAsc(conversationId)
            : messages.findByConversationIdAndIdGreaterThanOrderByIdAsc(conversationId, afterId);
        Map<Long, String> names = accounts.displayNames(found.stream().map(Message::getAuthorId).collect(java.util.stream.Collectors.toSet()));
        return found.stream()
            .map(m -> new MessageView(m.getId(), conversationId, m.getAuthorId(), names.getOrDefault(m.getAuthorId(), "?"), m.getContent(), m.getSentAt()))
            .toList();
    }

    /** "Is typing" indicator: not persisted, simply relayed to the participants. */
    @Transactional(readOnly = true)
    public void typing(Actor actor, long conversationId, boolean typing) {
        Conversation c = load(conversationId);
        requireParticipant(c, actor);
        if (c.getStatus() == ConversationStatus.CLOSED) {
            return;
        }
        String name = accounts.displayNames(Set.of(actor.userId())).getOrDefault(actor.userId(), "?");
        events.publishEvent(new ChatEvent(ChatEvent.Type.TYPING, conversationId, null, null, actor.userId(), name, typing));
    }

    /** Reads a conversation, with access control. */
    @Transactional(readOnly = true)
    public ConversationView get(Actor actor, long conversationId) {
        Conversation c = load(conversationId);
        requireParticipant(c, actor);
        return toView(c);
    }

    /** Used by the STOMP security: refuses subscribing to a conversation that is not the user's own. */
    @Transactional(readOnly = true)
    public void requireAccess(Actor actor, long conversationId) {
        requireParticipant(load(conversationId), actor);
    }

    // --- Access rules -------------------------------------------------------------------------------------------

    private Conversation load(long id) {
        return conversations.findById(id).orElseThrow(() -> ChatException.notFound("Conversation introuvable"));
    }

    /** Customer: their own conversation. Agent: only a conversation they have claimed. */
    private void requireParticipant(Conversation c, Actor actor) {
        boolean allowed = actor.isAgent()
            ? actor.userId() == orZero(c.getAgentId())
            : actor.userId() == c.getClientId();
        if (!allowed) {
            throw ChatException.forbidden("Accès refusé à cette conversation");
        }
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    private static void requireClient(Actor actor) {
        if (actor.isAgent()) {
            throw ChatException.forbidden("Réservé aux clients");
        }
    }

    private static void requireAgent(Actor actor) {
        if (!actor.isAgent()) {
            throw ChatException.forbidden("Réservé aux conseillers");
        }
    }

    private static String normalize(String raw) {
        String content = raw == null ? "" : raw.strip();
        if (content.isEmpty()) {
            throw ChatException.invalid("Le message est vide");
        }
        if (content.length() > Message.MAX_LENGTH) {
            throw ChatException.invalid("Le message dépasse " + Message.MAX_LENGTH + " caractères");
        }
        return content;
    }

    // --- Entity -> view conversion ---------------------------------------------------------------------------------

    private ConversationView toView(Conversation c) {
        return toViews(List.of(c)).getFirst();
    }

    /** A single query to resolve all the display names of the list. */
    private List<ConversationView> toViews(List<Conversation> list) {
        Set<Long> userIds = new java.util.HashSet<>();
        for (Conversation c : list) {
            userIds.add(c.getClientId());
            if (c.getAgentId() != null) {
                userIds.add(c.getAgentId());
            }
        }
        Map<Long, String> names = accounts.displayNames(userIds);
        return list.stream()
            .map(c -> new ConversationView(c.getId(), c.getStatus(), c.getClientId(), names.get(c.getClientId()),
                c.getAgentId(), c.getAgentId() == null ? null : names.get(c.getAgentId()),
                c.getReservationId(), c.getCreatedAt(), c.getClosedAt()))
            .toList();
    }
}
