package com.nihil.voice.persistence;

import java.time.Instant;
import java.util.UUID;

public record CallMessageData(UUID callId, UUID tenantId, UUID turnId, String providerEventId, String role,
                              String text, boolean finalMessage, long sequenceNumber, Instant startedAt, Instant endedAt) {}
