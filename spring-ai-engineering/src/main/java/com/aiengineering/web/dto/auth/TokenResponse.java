package com.aiengineering.web.dto.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {}
