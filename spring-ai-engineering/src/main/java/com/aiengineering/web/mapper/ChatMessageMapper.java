package com.aiengineering.web.mapper;

import com.aiengineering.domain.ChatMessage;
import com.aiengineering.web.dto.chat.ChatMessageResponse;
import org.mapstruct.Mapper;

// @Mapper is a MapStruct annotation that triggers compile-time code generation.
// MapStruct reads the method signatures below and generates a full implementation
// class at build time — no reflection at runtime, unlike ModelMapper.
//
// componentModel = "spring" tells MapStruct to annotate the generated implementation
// with @Component so Spring can discover it and inject it like any other bean.
// Without this, you would have to call Mappers.getMapper(ChatMessageMapper.class) manually.
@Mapper(componentModel = "spring")
public interface ChatMessageMapper {

    // MapStruct inspects ChatMessage fields and ChatMessageResponse fields
    // and generates field-by-field copy code. Fields with matching names are
    // mapped automatically; mismatches need @Mapping annotations.
    ChatMessageResponse toResponse(ChatMessage entity);
}
