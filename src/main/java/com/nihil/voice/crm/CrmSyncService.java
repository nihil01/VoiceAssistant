package com.nihil.voice.crm;

import reactor.core.publisher.Mono;

public final class CrmSyncService {
    private final TwentyCrmGateway gateway;

    public CrmSyncService(TwentyCrmGateway gateway) {
        this.gateway = gateway;
    }

    public Mono<String> sync(CrmCallData call) {
        String contactName = call.summary().contact() == null
                ? null
                : call.summary().contact().name();

        return gateway.findPersonIdByPhone(call.callerNumber())
                .switchIfEmpty(Mono.defer(() -> gateway.createPerson(
                        call.callerNumber(),
                        contactName
                )))
                .flatMap(personId -> syncCall(call, personId));
    }

    private Mono<String> syncCall(CrmCallData call, String personId) {
        return gateway.upsertAiCall(call, personId)
                .flatMap(aiCallId -> syncRelatedRecords(call, personId, aiCallId)
                        .thenReturn(aiCallId));
    }

    private Mono<Void> syncRelatedRecords(
            CrmCallData call,
            String personId,
            String aiCallId
    ) {
        Mono<Void> recording = call.hasRecording()
                ? gateway.upsertCallRecording(call, aiCallId).then()
                : Mono.empty();

        return recording
                .then(gateway.createNote(
                        personId,
                        aiCallId,
                        call.summary().shortSummary()
                ))
                .then(createTaskIfNeeded(call, personId, aiCallId));
    }

    private Mono<Void> createTaskIfNeeded(
            CrmCallData call,
            String personId,
            String aiCallId
    ) {
        var action = call.summary().nextAction();
        if (action == null || action.type() == null || action.type().isBlank()) {
            return Mono.empty();
        }
        return gateway.createTask(personId, aiCallId, action.type());
    }
}
