package com.aiengineering.repository;

import com.aiengineering.domain.ChatSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    // join fetch eagerly loads the associated User in a single query,
    // preventing a lazy-load N+1 hit when the caller accesses session.getUser().
    // The userId check enforces ownership — users can only fetch their own sessions.
    @Query(
            """
            select s from ChatSession s
            join fetch s.user u
            where s.id = :id and u.id = :userId
            """)
    Optional<ChatSession> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    // Returns a lightweight projection (id, title, createdAt) instead of full entities —
    // the session list page doesn't need the User association or updatedAt.
    // order by updatedAt desc shows most recently active sessions first.
    @Query(
            """
            select s.id as id, s.title as title, s.createdAt as createdAt
            from ChatSession s
            where s.user.id = :userId
            order by s.updatedAt desc
            """)
    List<ChatSessionListProjection> listForUser(@Param("userId") Long userId);

    // Projection interface for the list query above.
    // Spring Data generates an implementation proxy at runtime.
    interface ChatSessionListProjection {
        Long getId();

        String getTitle();

        java.time.Instant getCreatedAt();
    }
}
