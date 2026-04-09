package com.aiengineering.repository;

import com.aiengineering.domain.ChatSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    @Query(
            """
            select s from ChatSession s
            join fetch s.user u
            where s.id = :id and u.id = :userId
            """)
    Optional<ChatSession> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query(
            """
            select s.id as id, s.title as title, s.createdAt as createdAt
            from ChatSession s
            where s.user.id = :userId
            order by s.updatedAt desc
            """)
    List<ChatSessionListProjection> listForUser(@Param("userId") Long userId);

    interface ChatSessionListProjection {
        Long getId();

        String getTitle();

        java.time.Instant getCreatedAt();
    }
}
