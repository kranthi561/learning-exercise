package com.uuidservice.dto;

public record UUIDValidateResponse(String uuid, boolean exists, boolean valid, Long issuedAt, String tag) {}
