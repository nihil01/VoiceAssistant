package com.nihil.voice.persistence;
import com.nihil.voice.call.*;
import com.nihil.voice.llm.ConversationMessage;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
public final class R2dbcConversationMessageSink implements ConversationMessageSink {
 private final DatabaseClient db;private final UUID tenantId;
 public R2dbcConversationMessageSink(DatabaseClient db,UUID tenantId){this.db=db;this.tenantId=tenantId;}
 public Mono<Void> record(CallSession call,ConversationMessage.Role role,String text,String eventId,UUID turnId){
   var spec=db.sql("""
     insert into call_messages(call_id,tenant_id,turn_id,provider_event_id,role,text,is_final,sequence_number,started_at,ended_at)
     select :call,:tenant,:turn,:event,:role,:text,true,coalesce(max(sequence_number),0)+1,now(),now() from call_messages where call_id=:call
     on conflict do nothing
     """).bind("call",call.internalCallId()).bind("role",role.name().toLowerCase()).bind("text",text);
   spec=tenantId==null?spec.bindNull("tenant",UUID.class):spec.bind("tenant",tenantId);
   spec=turnId==null?spec.bindNull("turn",UUID.class):spec.bind("turn",turnId);
   spec=eventId==null?spec.bindNull("event",String.class):spec.bind("event",eventId);
   return spec.fetch().rowsUpdated().then();
 }
}
