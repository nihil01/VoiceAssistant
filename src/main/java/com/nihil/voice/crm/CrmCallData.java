package com.nihil.voice.crm;

import com.nihil.voice.summary.CallSummary;
import java.time.Instant;

public record CrmCallData(
        String externalCallId,
        String callerNumber,
        String destinationNumber,
        Instant startedAt,
        Instant endedAt,
        Long durationSeconds,
        String status,
        String recordingUrl,
        String transcript,
        CallSummary summary
) {
    public boolean hasRecording() {
        return recordingUrl != null && !recordingUrl.isBlank();
    }
}
