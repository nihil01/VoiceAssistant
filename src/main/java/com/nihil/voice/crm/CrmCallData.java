package com.nihil.voice.crm;

import com.nihil.voice.summary.CallSummary;
import java.time.Instant;

public record CrmCallData(String externalCallId, String callerNumber, String destinationNumber,
                          Instant startedAt, Instant endedAt, String transcript, CallSummary summary) {}
