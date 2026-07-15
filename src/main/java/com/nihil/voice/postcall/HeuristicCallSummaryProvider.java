package com.nihil.voice.postcall;
import java.util.Map;
import reactor.core.publisher.Mono;
public final class HeuristicCallSummaryProvider implements CallSummaryProvider {
 public Mono<CallSummary> summarize(PostCallCandidate call){String text=call.transcript()==null?"":call.transcript().strip();String shortText=text.length()>240?text.substring(0,240)+"…":text;return Mono.just(new CallSummary(shortText,text,null,null,null,null,null,Map.of()));}
}
