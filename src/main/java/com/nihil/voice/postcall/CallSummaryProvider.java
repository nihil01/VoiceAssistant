package com.nihil.voice.postcall;
import reactor.core.publisher.Mono;
public interface CallSummaryProvider { Mono<CallSummary> summarize(PostCallCandidate call); }
