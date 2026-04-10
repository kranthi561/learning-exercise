package com.aiengineering.repository;

import com.aiengineering.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// JpaRepository<User, Long> gives us standard CRUD + pagination for free.
// The second type parameter is the primary key type.
// Spring Data generates the implementation at startup — no class needed.
public interface UserRepository extends JpaRepository<User, Long> {

    // Spring Data derives the query from the method name:
    // findBy + EmailIgnoreCase → SELECT * FROM users WHERE LOWER(email) = LOWER(:email)
    // Returns Optional so callers handle "not found" without exceptions.
    Optional<User> findByEmailIgnoreCase(String email);

    // Derived query: SELECT COUNT(*) > 0 WHERE LOWER(email) = LOWER(:email)
    // Used during registration to check for duplicate emails before saving.
    boolean existsByEmailIgnoreCase(String email);

    // @Query with JPQL (not SQL) — uses entity/field names, not table/column names.
    // Returns a projection to avoid loading the full User entity when only
    // id/email/displayName are needed (lighter query, no password_hash transferred).
    @Query(
            """
            select u.id as id, u.email as email, u.displayName as displayName
            from User u
            where lower(u.email) like lower(concat('%', :q, '%'))
            order by u.email
            """)
    // @Param("q") binds the method argument to the :q placeholder in the JPQL above.
    List<UserSummaryProjection> searchSummaries(@Param("q") String q);

    // Spring Data Projections interface: the framework generates a proxy at runtime
    // that maps query result columns to these getter methods.
    // Avoids creating a separate DTO class just for this narrow query.
    interface UserSummaryProjection {
        Long getId();

        String getEmail();

        String getDisplayName();
    }
}
