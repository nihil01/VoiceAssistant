package com.nihil.voice.postcall;
import com.nihil.voice.llm.*;
import java.util.*;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
public final class LlmCallSummaryProvider implements CallSummaryProvider {
 private final LlmClient llm; private final ObjectMapper mapper;
 public LlmCallSummaryProvider(LlmClient llm,ObjectMapper mapper){this.llm=llm;this.mapper=mapper;}
 public Mono<CallSummary> summarize(PostCallCandidate call){
  String system="Return only one JSON object with fields shortSummary, fullSummary, intent, sentiment, leadQuality, outcome, nextAction, extractedData. Use null when unknown. Do not wrap JSON in markdown.";
  return llm.stream(List.of(new ConversationMessage(ConversationMessage.Role.SYSTEM,system),new ConversationMessage(ConversationMessage.Role.USER,call.transcript())))
   .collectList().map(parts->parse(String.join("",parts)));
 }
 @SuppressWarnings("unchecked") CallSummary parse(String raw){
  try{
   String json=raw.strip(); if(json.startsWith("```")){json=json.replaceFirst("^```(?:json)?\s*","").replaceFirst("\s*```$","");}
   Map<String,Object> m=mapper.readValue(json,Map.class);
   Object extracted=m.get("extractedData");
   return new CallSummary(str(m,"shortSummary"),str(m,"fullSummary"),str(m,"intent"),str(m,"sentiment"),str(m,"leadQuality"),str(m,"outcome"),str(m,"nextAction"),extracted instanceof Map<?,?> e?(Map<String,Object>)e:Map.of());
  }catch(Exception e){throw new IllegalArgumentException("Summary provider returned invalid JSON",e);}
 }
 private static String str(Map<String,Object> m,String key){Object v=m.get(key);return v==null?null:String.valueOf(v);}
}
