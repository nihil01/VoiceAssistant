package com.nihil.voice.crm;

import com.nihil.voice.summary.CallSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class R2dbcCrmSyncJobStore implements CrmSyncJobStore {
    private final DatabaseClient db;public R2dbcCrmSyncJobStore(DatabaseClient db){this.db=db;}
    @Override public Flux<CrmSyncWorkItem> claim(int limit){
        return db.sql("""
            with picked as (select id from crm_sync_jobs where status in ('PENDING','RETRY') and next_attempt_at<=now() and attempts<5 order by next_attempt_at limit :limit for update skip locked),
            claimed as (update crm_sync_jobs j set status='RUNNING',attempts=j.attempts+1,locked_at=now(),updated_at=now() from picked where j.id=picked.id returning j.id,j.call_id,j.attempts)
            select claimed.id job_id,claimed.attempts,c.external_call_id,c.caller_number,c.destination_number,c.started_at,c.ended_at,
              coalesce(s.short_summary,'Call completed') short_summary,s.full_summary,s.intent,s.sentiment,s.lead_quality,s.outcome,s.next_action,
              coalesce((select string_agg(m.role||': '||m.text,E'\n' order by m.sequence_number) from call_messages m where m.call_id=c.id and m.is_final),'') transcript
            from claimed join calls c on c.id=claimed.call_id left join call_summaries s on s.call_id=c.id
            """).bind("limit",limit).map((row,meta)->{
                var summary=new CallSummary(row.get("short_summary",String.class),row.get("full_summary",String.class),row.get("intent",String.class),row.get("sentiment",String.class),row.get("lead_quality",String.class),row.get("outcome",String.class),new CallSummary.Contact(null,row.get("caller_number",String.class)),List.of(),row.get("next_action",String.class)==null?null:new CallSummary.NextAction(row.get("next_action",String.class),null),Map.of());
                var call=new CrmCallData(row.get("external_call_id",String.class),row.get("caller_number",String.class),row.get("destination_number",String.class),row.get("started_at",Instant.class),row.get("ended_at",Instant.class),row.get("transcript",String.class),summary);
                return new CrmSyncWorkItem(row.get("job_id",UUID.class),row.get("attempts",Integer.class),call);
            }).all();
    }
    @Override public Mono<Void> complete(UUID id,String remote){return db.sql("update crm_sync_jobs set status='COMPLETED',remote_ids=jsonb_build_object('aiCallId',:remote),locked_at=null,updated_at=now() where id=:id").bind("remote",remote).bind("id",id).fetch().rowsUpdated().then();}
    @Override public Mono<Void> fail(UUID id,int attempts,String error){
        String safe=error==null?"unknown":error.substring(0,Math.min(error.length(),2000));
        return db.sql("update crm_sync_jobs set status=case when :attempts>=5 then 'FAILED' else 'RETRY' end,next_attempt_at=now()+(least(300,power(2,:attempts))||' seconds')::interval,last_error=:error,locked_at=null,updated_at=now() where id=:id")
            .bind("attempts",attempts).bind("error",safe).bind("id",id).fetch().rowsUpdated().then();
    }
}
