package com.aiengineering.web.controller;

import com.aiengineering.security.SecurityUtils;
import com.aiengineering.service.KnowledgeService;
import com.aiengineering.web.dto.knowledge.KnowledgeIngestRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void ingest(@Valid @RequestBody KnowledgeIngestRequest request) {
        log.debug("ingest: textLength={}, metadataKeys={}", request.text().length(), request.metadata().keySet());
        SecurityUtils.requireCurrentUser();
        knowledgeService.ingest(request.text(), request.metadata());
    }
}
