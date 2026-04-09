package com.aiengineering.web.mapper;

import com.aiengineering.domain.User;
import com.aiengineering.repository.UserRepository;
import com.aiengineering.web.dto.user.UserResponse;
import com.aiengineering.web.dto.user.UserSummaryResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);

    UserSummaryResponse toSummary(UserRepository.UserSummaryProjection projection);
}
