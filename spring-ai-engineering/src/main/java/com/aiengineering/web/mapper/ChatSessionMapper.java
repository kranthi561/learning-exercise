package com.aiengineering.web.mapper;

import com.aiengineering.repository.ChatSessionRepository;
import com.aiengineering.web.dto.chat.ChatSessionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ChatSessionMapper {

    ChatSessionResponse fromListProjection(ChatSessionRepository.ChatSessionListProjection projection);
}
