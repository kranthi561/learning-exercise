package com.uuidservice.controller;

import com.uuidservice.dto.*;
import com.uuidservice.service.UUIDService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * UUID REST API
 *
 * POST   /api/v1/uuid            → Issue single UUID
 * POST   /api/v1/uuid/batch      → Issue N UUIDs (max 1000)
 * GET    /api/v1/uuid/{uuid}     → Validate / lookup UUID
 * DELETE /api/v1/uuid/{uuid}     → Invalidate UUID
 * GET    /api/v1/uuid/stats      → Live stats
 */
@RestController
@RequestMapping("/api/v1/uuid")
public class UUIDController {

    private final UUIDService service;

    public UUIDController(UUIDService service) {
        this.service = service;
    }

    // ── Issue single UUID ────────────────────────────────────────────────────

    /**
     * POST /api/v1/uuid
     * Body (optional): { "tag": "order" }
     *
     * Response:
     * {
     *   "uuid":     "a1b2c3d4-e5f6-...",
     *   "issuedAt": 1704067200000,
     *   "tag":      "order"
     * }
     */
    @PostMapping
    public ResponseEntity<UUIDResponse> issue(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {

        String tag      = body != null ? body.getOrDefault("tag", "") : "";
        String clientIp = extractIp(request);

        UUIDResponse response = service.issue(clientIp, tag);
        return ResponseEntity.ok(response);
    }

    // ── Issue batch ──────────────────────────────────────────────────────────

    /**
     * POST /api/v1/uuid/batch
     * Body: { "count": 500, "tag": "session" }
     *
     * Response:
     * {
     *   "uuids":          ["...", "...", ...],
     *   "count":          500,
     *   "generatedInMs":  12
     * }
     */
    @PostMapping("/batch")
    public ResponseEntity<?> issueBatch(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        int count = body.containsKey("count")
            ? Integer.parseInt(body.get("count").toString())
            : 1;

        String tag      = body.containsKey("tag") ? body.get("tag").toString() : "";
        String clientIp = extractIp(request);

        try {
            UUIDBatchResponse response = service.issueBatch(count, clientIp, tag);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Validate / Lookup ────────────────────────────────────────────────────

    /**
     * GET /api/v1/uuid/{uuid}
     *
     * Response:
     * {
     *   "uuid":     "...",
     *   "exists":   true,
     *   "valid":    true,
     *   "issuedAt": 1704067200000,
     *   "tag":      "order"
     * }
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<UUIDValidateResponse> validate(@PathVariable String uuid) {
        return ResponseEntity.ok(service.validate(uuid));
    }

    // ── Invalidate ───────────────────────────────────────────────────────────

    /**
     * DELETE /api/v1/uuid/{uuid}
     */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Map<String, Object>> invalidate(@PathVariable String uuid) {
        boolean success = service.invalidate(uuid);
        if (success) {
            return ResponseEntity.ok(Map.of("uuid", uuid, "invalidated", true));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Stats ────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/uuid/stats
     *
     * Response:
     * {
     *   "totalIssued":     10000,
     *   "collisionBlocks": 0,
     *   "registrySize":    10000,
     *   "poolAvailable":   40000
     * }
     */
    @GetMapping("/stats")
    public ResponseEntity<UUIDStatsResponse> stats() {
        return ResponseEntity.ok(service.stats());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
