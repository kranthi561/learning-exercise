package com.aiengineering.repository;

import com.aiengineering.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query(
            """
            select u.id as id, u.email as email, u.displayName as displayName
            from User u
            where lower(u.email) like lower(concat('%', :q, '%'))
            order by u.email
            """)
    List<UserSummaryProjection> searchSummaries(@Param("q") String q);

    interface UserSummaryProjection {
        Long getId();

        String getEmail();

        String getDisplayName();
    }
}
