package com.nihil.voice.crm;

import reactor.core.publisher.Mono;

public interface TwentyCrmGateway {
    Mono<String> findPersonIdByPhone(String phone);

    Mono<String> createPerson(String phone, String displayName);

    Mono<String> upsertAiCall(CrmCallData call, String personId);

    Mono<String> upsertCallRecording(CrmCallData call, String aiCallId);

    Mono<Void> createNote(String personId, String aiCallId, String body);

    Mono<Void> createTask(String personId, String aiCallId, String title);
}
