package com.instagram.mcp.mcp.dto;

public record InstagramPostMcpResponse(boolean success, String mediaId, String imageFile, String caption, String error) {
}
