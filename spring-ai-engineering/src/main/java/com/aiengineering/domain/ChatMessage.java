package com.aiengineering.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "chat_messages")
@Getter
@Setter
public class ChatMessage extends BaseAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many messages belong to one chat session.
    // LAZY fetch avoids loading the full session object just to write a message.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    // EnumType.STRING stores "USER" / "ASSISTANT" / "SYSTEM" as text in the DB,
    // making it readable and safe to rename/reorder enum constants without
    // breaking existing rows (unlike EnumType.ORDINAL which stores an integer index).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;

    // columnDefinition = "text" maps to PostgreSQL TEXT (unlimited length)
    // instead of the default VARCHAR(255), because AI responses can be very long.
    @Column(nullable = false, columnDefinition = "text")
    private String content;
}
