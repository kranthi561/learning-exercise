package com.aiengineering.repository;

import com.aiengineering.domain.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

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

    interface MessageSnippetProjection {
        Long getId();

        com.aiengineering.domain.MessageRole getRole();

        String getExcerpt();

        java.time.Instant getCreatedAt();
    }
}
