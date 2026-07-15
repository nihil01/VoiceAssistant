package com.nihil.voice.postcall;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
public interface PostCallStore {
 Flux<PostCallCandidate> pending(int limit);
 Mono<Void> complete(PostCallCandidate call,CallSummary summary);
 Mono<Void> fail(PostCallCandidate call,Throwable error);
}
