package com.uuidservice.dto;

public record UUIDStatsResponse(long totalIssued, long collisionBlocks, int registrySize) {}
