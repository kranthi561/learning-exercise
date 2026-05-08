package com.uuidservice.service;

import com.uuidservice.core.CustomUUIDGenerator;
import com.uuidservice.core.UUIDTracker;
import com.uuidservice.dto.UUIDBatchResponse;
import com.uuidservice.dto.UUIDResponse;
import com.uuidservice.dto.UUIDStatsResponse;
import com.uuidservice.dto.UUIDValidateResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UUIDService {

    private final CustomUUIDGenerator generator;
    private final UUIDTracker         tracker;

    public UUIDService(CustomUUIDGenerator generator, UUIDTracker tracker) {
        this.generator = generator;
        this.tracker   = tracker;
    }

    public UUIDResponse issue(String clientIp, String tag) {
        String uuid = issueWithRetry(clientIp, tag);
        return new UUIDResponse(uuid, Instant.now().toEpochMilli(), tag);
    }

    public UUIDBatchResponse issueBatch(int count, String clientIp, String tag) {
        if (count < 1 || count > 1000)
            throw new IllegalArgumentException("count must be 1–1000");

        long start = System.currentTimeMillis();
        List<String> uuids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            uuids.add(issueWithRetry(clientIp, tag));
        }
        long elapsed = System.currentTimeMillis() - start;
        return new UUIDBatchResponse(uuids, elapsed, count);
    }

    public UUIDValidateResponse validate(String uuid) {
        Optional<UUIDTracker.UUIDRecord> record = tracker.lookup(uuid);
        if (record.isEmpty()) {
            return new UUIDValidateResponse(uuid, false, false, null, null);
        }
        UUIDTracker.UUIDRecord r = record.get();
        return new UUIDValidateResponse(uuid, true, r.valid(), r.issuedAt(), r.tag());
    }

    public boolean invalidate(String uuid) {
        return tracker.invalidate(uuid);
    }

    public UUIDStatsResponse stats() {
        return new UUIDStatsResponse(
            tracker.totalIssued(),
            tracker.collisionBlocks(),
            tracker.registrySize()
        );
    }

    private String issueWithRetry(String clientIp, String tag) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String uuid = generator.generate();
            if (tracker.register(uuid, clientIp, tag)) return uuid;
        }
        throw new RuntimeException("Failed to issue unique UUID after 3 attempts — this should never happen");
    }
}
