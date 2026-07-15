package com.nihil.voice.postcall;
import java.util.UUID;
public record PostCallCandidate(UUID callId, UUID tenantId, String transcript) {}
