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
    private static final String CLAIM_SQL = """
            with picked as (
                select id
                from crm_sync_jobs
                where (
                    (
                        status in ('PENDING', 'RETRY')
                        and next_attempt_at <= now()
                    ) or (
                        status = 'RUNNING'
                        and locked_at < now() - interval '5 minutes'
                    )
                )
                and attempts < 5
                order by next_attempt_at
                limit :limit
                for update skip locked
            ),
            claimed as (
                update crm_sync_jobs job
                set status = 'RUNNING',
                    attempts = job.attempts + 1,
                    locked_at = now(),
                    updated_at = now()
                from picked
                where job.id = picked.id
                returning job.id, job.call_id, job.attempts
            )
            select claimed.id as job_id,
                   claimed.attempts,
                   calls.external_call_id,
                   calls.caller_number,
                   calls.destination_number,
                   calls.started_at,
                   calls.ended_at,
                   calls.duration_seconds,
                   calls.status,
                   calls.recording_url,
                   coalesce(summary.short_summary, 'Call completed') as short_summary,
                   summary.full_summary,
                   summary.intent,
                   summary.sentiment,
                   summary.lead_quality,
                   summary.outcome,
                   summary.next_action,
                   coalesce((
                       select string_agg(
                           message.role || ': ' || message.text,
                           E'\n' order by message.sequence_number
                       )
                       from call_messages message
                       where message.call_id = calls.id
                         and message.is_final
                   ), '') as transcript
            from claimed
            join calls on calls.id = claimed.call_id
            left join call_summaries summary on summary.call_id = calls.id
            """;

    private final DatabaseClient db;

    public R2dbcCrmSyncJobStore(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Flux<CrmSyncWorkItem> claim(int limit) {
        return db.sql(CLAIM_SQL)
                .bind("limit", limit)
                .map((row, metadata) -> {
                    String callerNumber = row.get("caller_number", String.class);
                    String nextAction = row.get("next_action", String.class);
                    var summary = new CallSummary(
                            row.get("short_summary", String.class),
                            row.get("full_summary", String.class),
                            row.get("intent", String.class),
                            row.get("sentiment", String.class),
                            row.get("lead_quality", String.class),
                            row.get("outcome", String.class),
                            new CallSummary.Contact(null, callerNumber),
                            List.of(),
                            nextAction == null
                                    ? null
                                    : new CallSummary.NextAction(nextAction, null),
                            Map.of()
                    );
                    var call = new CrmCallData(
                            row.get("external_call_id", String.class),
                            callerNumber,
                            row.get("destination_number", String.class),
                            row.get("started_at", Instant.class),
                            row.get("ended_at", Instant.class),
                            row.get("duration_seconds", Long.class),
                            row.get("status", String.class),
                            row.get("recording_url", String.class),
                            row.get("transcript", String.class),
                            summary
                    );
                    return new CrmSyncWorkItem(
                            row.get("job_id", UUID.class),
                            row.get("attempts", Integer.class),
                            call
                    );
                })
                .all();
    }

    @Override
    public Mono<Void> complete(UUID jobId, String remoteAiCallId) {
        return db.sql("""
                with completed as (
                    update crm_sync_jobs
                    set status = 'COMPLETED',
                        remote_ids = jsonb_build_object('aiCallId', :remoteId),
                        locked_at = null,
                        updated_at = now()
                    where id = :jobId
                    returning call_id
                )
                update calls
                set twenty_record_id = cast(:remoteId as uuid),
                    updated_at = now()
                from completed
                where calls.id = completed.call_id
                """)
                .bind("remoteId", remoteAiCallId)
                .bind("jobId", jobId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<Void> fail(UUID jobId, int attempts, String error) {
        String safeError = error == null
                ? "unknown"
                : error.substring(0, Math.min(error.length(), 2_000));

        return db.sql("""
                update crm_sync_jobs
                set status = case when :attempts >= 5 then 'FAILED' else 'RETRY' end,
                    next_attempt_at = now()
                        + (least(300, power(2, :attempts)) || ' seconds')::interval,
                    last_error = :error,
                    locked_at = null,
                    updated_at = now()
                where id = :jobId
                """)
                .bind("attempts", attempts)
                .bind("error", safeError)
                .bind("jobId", jobId)
                .fetch()
                .rowsUpdated()
                .then();
    }
}
