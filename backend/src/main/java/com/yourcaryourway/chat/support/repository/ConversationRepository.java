package com.yourcaryourway.chat.support.repository;

import com.yourcaryourway.chat.support.domain.Conversation;
import com.yourcaryourway.chat.support.domain.ConversationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByClientIdAndStatusNot(Long clientId, ConversationStatus status);

    List<Conversation> findByClientIdOrderByCreatedAtDesc(Long clientId);

    /** Waiting queue: oldest first (partial index idx_conversation_queue). */
    List<Conversation> findByStatusOrderByCreatedAtAsc(ConversationStatus status);

    List<Conversation> findByAgentIdAndStatusOrderByCreatedAtAsc(Long agentId, ConversationStatus status);

    /**
     * Atomic claim: the UPDATE only succeeds if the conversation is still waiting.
     * If two agents click at the same time, PostgreSQL lets only one through (returns 1 row), the other gets 0.
     */
    default int claim(Long id, Long agentId) {
        return assign(id, agentId, ConversationStatus.WAITING, ConversationStatus.OPEN);
    }

    /** Closing by the agent who claimed the conversation (returns 0 if it is not them or if it is already closed). */
    default int close(Long id, Long agentId, Instant now) {
        return finish(id, agentId, now, ConversationStatus.OPEN, ConversationStatus.CLOSED);
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Conversation c set c.agentId = :agentId, c.status = :to where c.id = :id and c.status = :from")
    int assign(@Param("id") Long id, @Param("agentId") Long agentId,
               @Param("from") ConversationStatus from, @Param("to") ConversationStatus to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Conversation c set c.status = :to, c.closedAt = :now where c.id = :id and c.agentId = :agentId and c.status = :from")
    int finish(@Param("id") Long id, @Param("agentId") Long agentId, @Param("now") Instant now,
               @Param("from") ConversationStatus from, @Param("to") ConversationStatus to);
}
