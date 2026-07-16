package com.nihil.voice.crm;

import static org.assertj.core.api.Assertions.*;
import com.nihil.voice.summary.CallSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CrmSyncServiceTest {
    @Test
    void existingPersonIsReusedAndCallNoteAndTaskAreSynchronized() {
        var gateway = new RecordingGateway(true);
        var summary = new CallSummary("Callback requested", null, "purchase", "positive", "warm", "callback_requested",
            new CallSummary.Contact("Elvin", "+994501234567"), List.of(), new CallSummary.NextAction("manager_callback", null), Map.of());
        var call = new CrmCallData(
                "external-1",
                "+994****4567",
                "700",
                Instant.now(),
                Instant.now(),
                42L,
                "ENDED",
                "https://recordings.example/call.wav",
                "text",
                summary
        );

        StepVerifier.create(new CrmSyncService(gateway).sync(call)).expectNext("ai-call-1").verifyComplete();
        assertThat(gateway.personCreates).hasValue(0);
        assertThat(gateway.upserts).hasValue(1);
        assertThat(gateway.recordings).hasValue(1);
        assertThat(gateway.notes).hasValue(1);
        assertThat(gateway.tasks).hasValue(1);
    }

    private static final class RecordingGateway implements TwentyCrmGateway {
        final boolean existing;
        final AtomicInteger personCreates = new AtomicInteger();
        final AtomicInteger upserts = new AtomicInteger();
        final AtomicInteger recordings = new AtomicInteger();
        final AtomicInteger notes = new AtomicInteger();
        final AtomicInteger tasks = new AtomicInteger();

        RecordingGateway(boolean existing) { this.existing = existing; }
        public Mono<String> findPersonIdByPhone(String phone) { return existing ? Mono.just("person-1") : Mono.empty(); }
        public Mono<String> createPerson(String phone, String name) { personCreates.incrementAndGet(); return Mono.just("person-1"); }
        public Mono<String> upsertAiCall(CrmCallData call, String person) { upserts.incrementAndGet(); return Mono.just("ai-call-1"); }
        public Mono<String> upsertCallRecording(CrmCallData call, String aiCallId) { recordings.incrementAndGet(); return Mono.just("recording-1"); }
        public Mono<Void> createNote(String person, String call, String body) { notes.incrementAndGet(); return Mono.empty(); }
        public Mono<Void> createTask(String person, String call, String title) { tasks.incrementAndGet(); return Mono.empty(); }
    }
}
