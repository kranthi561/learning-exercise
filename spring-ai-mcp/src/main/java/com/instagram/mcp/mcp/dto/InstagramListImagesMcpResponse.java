package com.instagram.mcp.mcp.dto;

import java.util.List;

public record InstagramListImagesMcpResponse(String folder, int count, List<String> images) {
}
