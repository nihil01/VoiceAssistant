package com.nihil.voice.postcall;
import reactor.core.publisher.Mono;
public final class PostCallProcessor {
 private final PostCallStore store;private final CallSummaryProvider provider;
 public PostCallProcessor(PostCallStore store,CallSummaryProvider provider){this.store=store;this.provider=provider;}
 public Mono<Void> runOnce(){return store.pending(10).concatMap(call->provider.summarize(call).flatMap(summary->store.complete(call,summary)).onErrorResume(error->store.fail(call,error))).then();}
}
