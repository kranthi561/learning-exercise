package com.aiengineering.web.mapper;

import com.aiengineering.repository.ChatSessionRepository;
import com.aiengineering.web.dto.chat.ChatSessionResponse;
import org.mapstruct.Mapper;

// componentModel = "spring" — MapStruct generates a @Component implementation
// so Spring can inject this mapper into ChatSessionService without any manual wiring.
@Mapper(componentModel = "spring")
public interface ChatSessionMapper {

    // Maps from a Spring Data projection (ChatSessionListProjection) to a DTO.
    // MapStruct matches getter names on the projection interface (getId, getTitle, getCreatedAt)
    // to constructor parameters or setters on ChatSessionResponse by name — no @Mapping needed
    // as long as the names align.
    ChatSessionResponse fromListProjection(ChatSessionRepository.ChatSessionListProjection projection);
}
