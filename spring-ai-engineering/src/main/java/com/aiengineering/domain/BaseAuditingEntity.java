package com.aiengineering.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// Lombok: generates getters/setters for createdAt and updatedAt
// so subclasses and mappers can access them without boilerplate.
@Getter
@Setter

// Tells JPA that this class is not an entity table by itself —
// its columns are inherited and merged into each concrete subclass's table.
@MappedSuperclass

// Wires the Spring Data auditing infrastructure into JPA's entity lifecycle.
// Without this listener, @CreatedDate / @LastModifiedDate are ignored.
// @EnableJpaAuditing in the main class activates the auditing mechanism globally.
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditingEntity {

    // Automatically set to the current timestamp when the entity is first persisted.
    @CreatedDate
    // updatable = false prevents anyone from overwriting the creation time on updates.
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Automatically updated to the current timestamp on every save/merge.
    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
