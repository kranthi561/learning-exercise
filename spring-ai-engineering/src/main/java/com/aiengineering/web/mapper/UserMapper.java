package com.aiengineering.web.mapper;

import com.aiengineering.domain.User;
import com.aiengineering.repository.UserRepository;
import com.aiengineering.web.dto.user.UserResponse;
import com.aiengineering.web.dto.user.UserSummaryResponse;
import org.mapstruct.Mapper;

// componentModel = "spring" — MapStruct generates a Spring @Component so this
// mapper is injectable wherever UserService needs it.
@Mapper(componentModel = "spring")
public interface UserMapper {

    // Maps a full User entity to UserResponse.
    // passwordHash is excluded automatically because UserResponse has no such field —
    // MapStruct only copies fields that exist on both sides, so sensitive data
    // never leaks into the API response by accident.
    UserResponse toResponse(User user);

    // Maps a lightweight Spring Data projection (id, email, displayName) to a summary DTO.
    // Used for the search endpoint where loading full User entities would be wasteful.
    UserSummaryResponse toSummary(UserRepository.UserSummaryProjection projection);
}
