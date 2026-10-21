package com.yourcaryourway.chat.support.repository;

import com.yourcaryourway.chat.support.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByIdAsc(Long conversationId);

    /** Catch-up after a network drop: only the messages newer than the last one received. */
    List<Message> findByConversationIdAndIdGreaterThanOrderByIdAsc(Long conversationId, Long afterId);
}
