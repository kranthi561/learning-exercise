package com.aiengineering.web.mapper;

import com.aiengineering.domain.ChatMessage;
import com.aiengineering.web.dto.chat.ChatMessageResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ChatMessageMapper {

    ChatMessageResponse toResponse(ChatMessage entity);
}
