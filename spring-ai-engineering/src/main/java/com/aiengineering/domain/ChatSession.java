package com.aiengineering.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "chat_sessions")
@Getter
@Setter
public class ChatSession extends BaseAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many sessions can belong to one user.
    // FetchType.LAZY means the User is NOT loaded from the DB until explicitly accessed —
    // this avoids an unnecessary JOIN on every session query.
    // optional = false adds a NOT NULL constraint at the JPA level.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    // Maps the foreign-key column in the chat_sessions table to the users.id primary key.
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;
}
