package com.nihil.voice.postcall;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
public final class R2dbcPostCallStore implements PostCallStore {
 private final DatabaseClient db;private final TransactionalOperator tx;private final ObjectMapper mapper;
 public R2dbcPostCallStore(DatabaseClient db,TransactionalOperator tx,ObjectMapper mapper){this.db=db;this.tx=tx;this.mapper=mapper;}
 public Flux<PostCallCandidate> pending(int limit){return db.sql("""
   select j.call_id,c.tenant_id,coalesce(string_agg(m.role || ': ' || m.text, E'\n' order by m.sequence_number),'') transcript
   from post_call_jobs j join calls c on c.id=j.call_id left join call_messages m on m.call_id=c.id
   where j.status in ('PENDING','RETRY') and j.next_attempt_at<=now()
   group by j.call_id,c.tenant_id,j.next_attempt_at order by j.next_attempt_at limit :limit
   """).bind("limit",limit).map((row,meta)->new PostCallCandidate(row.get("call_id",UUID.class),row.get("tenant_id",UUID.class),row.get("transcript",String.class))).all();}
 public Mono<Void> complete(PostCallCandidate call,CallSummary s){
  String extracted;try{extracted=mapper.writeValueAsString(s.extractedData());}catch(Exception e){return Mono.error(e);}
  var summary=db.sql("""
   insert into call_summaries(call_id,short_summary,full_summary,intent,sentiment,lead_quality,outcome,next_action,extracted_data_json)
   values(:call,:short,:full,:intent,:sentiment,:quality,:outcome,:action,cast(:data as jsonb))
   on conflict(call_id) do update set short_summary=excluded.short_summary,full_summary=excluded.full_summary,intent=excluded.intent,
   sentiment=excluded.sentiment,lead_quality=excluded.lead_quality,outcome=excluded.outcome,next_action=excluded.next_action,extracted_data_json=excluded.extracted_data_json
   """);
  summary=bindNullable(summary,"call",call.callId(),UUID.class);summary=bindNullable(summary,"short",s.shortSummary(),String.class);summary=bindNullable(summary,"full",s.fullSummary(),String.class);summary=bindNullable(summary,"intent",s.intent(),String.class);summary=bindNullable(summary,"sentiment",s.sentiment(),String.class);summary=bindNullable(summary,"quality",s.leadQuality(),String.class);summary=bindNullable(summary,"outcome",s.outcome(),String.class);summary=bindNullable(summary,"action",s.nextAction(),String.class);summary=summary.bind("data",extracted);
  var crmSpec=db.sql("insert into crm_sync_jobs(tenant_id,call_id,external_call_id) values(:tenant,:call,:external) on conflict(external_call_id) do nothing")
   .bind("call",call.callId()).bind("external",call.callId().toString());
  crmSpec=bindNullable(crmSpec,"tenant",call.tenantId(),UUID.class);
  Mono<Void> crm=crmSpec.fetch().rowsUpdated().then();
  var eventSpec=db.sql("insert into event_outbox(tenant_id,aggregate_id,event_type,payload) values(:tenant,:call,'PostCallCompleted',jsonb_build_object('callId',cast(:external as text)))")
   .bind("call",call.callId()).bind("external",call.callId().toString());
  eventSpec=bindNullable(eventSpec,"tenant",call.tenantId(),UUID.class);
  Mono<Void> event=eventSpec.fetch().rowsUpdated().then();
  Mono<Void> done=db.sql("update post_call_jobs set status='COMPLETED',updated_at=now(),last_error=null where call_id=:call").bind("call",call.callId()).fetch().rowsUpdated().then();
  return tx.transactional(summary.fetch().rowsUpdated().then().then(crm).then(event).then(done));
 }
 public Mono<Void> fail(PostCallCandidate call,Throwable error){String message=error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();if(message.length()>1000)message=message.substring(0,1000);return db.sql("update post_call_jobs set status='RETRY',attempts=attempts+1,next_attempt_at=now()+least(interval '1 hour',interval '5 seconds'*power(2,least(attempts,8))),last_error=:error,updated_at=now() where call_id=:call").bind("error",message).bind("call",call.callId()).fetch().rowsUpdated().then();}
 private static <T> DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec,String name,T value,Class<T> type){return value==null?spec.bindNull(name,type):spec.bind(name,value);}
}
