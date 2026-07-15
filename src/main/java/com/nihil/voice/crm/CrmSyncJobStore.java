package com.nihil.voice.crm;

import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CrmSyncJobStore {
    Flux<CrmSyncWorkItem> claim(int limit);
    Mono<Void> complete(UUID jobId,String remoteAiCallId);
    Mono<Void> fail(UUID jobId,int attempts,String error);
}
