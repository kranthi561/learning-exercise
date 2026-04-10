package com.aiengineering.repository;

import com.aiengineering.domain.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // Returns all messages for a session in chronological order (asc).
    // The join on s.user u + u.id = :userId ensures a user cannot read
    // messages from sessions they don't own.
    // Secondary sort by m.id handles messages created within the same millisecond.
    @Query(
            """
            select m from ChatMessage m
            join m.session s
            join s.user u
            where s.id = :sessionId and u.id = :userId
            order by m.createdAt asc, m.id asc
            """)
    List<ChatMessage> findHistoryForSession(
            @Param("sessionId") Long sessionId, @Param("userId") Long userId);

    // Returns a lightweight snippet projection for the session preview —
    // substring(content, 1, 200) avoids transferring large AI responses
    // just to show a short excerpt in the UI.
    // Ordered by createdAt desc so the most recent messages come first.
    @Query(
            """
            select m.id as id, m.role as role, substring(m.content, 1, 200) as excerpt, m.createdAt as createdAt
            from ChatMessage m
            join m.session s
            join s.user u
            where s.id = :sessionId and u.id = :userId
            order by m.createdAt desc
            """)
    List<MessageSnippetProjection> findRecentSnippets(
            @Param("sessionId") Long sessionId, @Param("userId") Long userId);

    // Projection interface — Spring Data generates a proxy at runtime.
    // Only exposes the columns selected above; no full entity load.
    interface MessageSnippetProjection {
        Long getId();

        com.aiengineering.domain.MessageRole getRole();

        String getExcerpt();

        java.time.Instant getCreatedAt();
    }
}
