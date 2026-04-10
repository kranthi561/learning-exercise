package com.aiengineering.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// Marks this class as a JPA entity — Hibernate maps it to a database table.
@Entity

// Explicitly names the table 'users' to avoid conflicts with SQL reserved words
// (e.g. 'user' is a reserved keyword in PostgreSQL).
@Table(name = "users")

// Lombok: generates getters and setters for all fields below.
@Getter
@Setter

// Inherits createdAt and updatedAt columns from BaseAuditingEntity.
public class User extends BaseAuditingEntity {

    // Marks this field as the primary key.
    @Id
    // IDENTITY delegates ID generation to the database's auto-increment / SERIAL column.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // unique = true adds a DB-level unique constraint so duplicate emails are rejected
    // even under concurrent inserts. length = 255 matches the default VARCHAR size.
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // Stored as a BCrypt hash — never the plain-text password.
    // The column name deviates from the field name to be explicit in the schema.
    @Column(nullable = false, name = "password_hash")
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String displayName;

    // Explicit getters below override Lombok's generated ones for the same fields.
    // They are kept here intentionally — remove the duplicates if Lombok is sufficient.
    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }
}
