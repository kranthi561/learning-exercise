package com.uuidservice.dto;

import java.util.List;

public record UUIDBatchResponse(List<String> uuids, long generatedInMs, int count) {}
