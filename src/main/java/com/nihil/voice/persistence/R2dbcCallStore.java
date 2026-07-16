package com.nihil.voice.persistence;

import com.nihil.voice.call.CallSession;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

public final class R2dbcCallStore implements ReactiveCallStore {
    private final DatabaseClient db;
    public R2dbcCallStore(DatabaseClient db) { this.db = db; }

    @Override public Mono<Void> create(CallSession call, UUID tenantId, UUID assistantId) {
        var spec = db.sql("""
            insert into calls(id, tenant_id, assistant_id, external_call_id, asterisk_channel_id,
                              media_channel_id, bridge_id, caller_number, destination_number, status, started_at)
            values(:id, :tenant, :assistant, :external, :channel, :media, :bridge, :caller, :destination, :status, :started)
            on conflict (external_call_id) do nothing
            """)
            .bind("id", call.internalCallId()).bind("external", call.asteriskChannelId())
            .bind("channel", call.asteriskChannelId()).bind("status", call.state().name()).bind("started", call.startedAt());
        spec = bindNullable(spec, "tenant", tenantId, UUID.class);
        spec = bindNullable(spec, "assistant", assistantId, UUID.class);
        spec = bindNullable(spec, "media", call.mediaChannelId(), String.class);
        spec = bindNullable(spec, "bridge", call.bridgeId(), String.class);
        spec = bindNullable(spec, "caller", call.callerNumber(), String.class);
        spec = bindNullable(spec, "destination", call.destinationNumber(), String.class);
        return spec.fetch().rowsUpdated().then();
    }

    @Override public Mono<Void> appendMessage(CallMessageData m) {
        var spec = db.sql("""
            insert into call_messages(call_id, tenant_id, turn_id, provider_event_id, role, text, is_final,
                                      sequence_number, started_at, ended_at)
            values(:call, :tenant, :turn, :event, :role, :text, :final, :sequence, :started, :ended)
            on conflict do nothing
            """).bind("call", m.callId()).bind("role", m.role()).bind("text", m.text())
            .bind("final", m.finalMessage()).bind("sequence", m.sequenceNumber());
        spec = bindNullable(spec, "tenant", m.tenantId(), UUID.class);
        spec = bindNullable(spec, "turn", m.turnId(), UUID.class);
        spec = bindNullable(spec, "event", m.providerEventId(), String.class);
        spec = bindNullable(spec, "started", m.startedAt(), Instant.class);
        spec = bindNullable(spec, "ended", m.endedAt(), Instant.class);
        return spec.fetch().rowsUpdated().then();
    }

    @Override public Mono<Void> end(UUID callId, String status, String hangupCause, Instant endedAt) {
        return db.sql("""
            update calls set status=:status, hangup_cause=:cause, ended_at=:ended,
              duration_seconds=greatest(0, extract(epoch from (:ended - started_at))::bigint), updated_at=now()
            where id=:id and ended_at is null
            """).bind("status", status).bind("ended", endedAt).bind("id", callId)
            .bind("cause", hangupCause == null ? "unknown" : hangupCause).fetch().rowsUpdated().then();
    }

    @Override public Mono<Void> enqueueCrmSync(UUID callId, UUID tenantId, String externalCallId) {
        var spec = db.sql("""
            insert into crm_sync_jobs(tenant_id, call_id, external_call_id) values(:tenant, :call, :external)
            on conflict (external_call_id) do nothing
            """).bind("call", callId).bind("external", externalCallId);
        spec = bindNullable(spec, "tenant", tenantId, UUID.class);
        return spec.fetch().rowsUpdated().then();
    }

    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }
    @Override public Mono<Void> enqueuePostCall(UUID callId) {
        return db.sql("insert into post_call_jobs(call_id) values(:call) on conflict(call_id) do nothing")
            .bind("call",callId).fetch().rowsUpdated().then();
    }
}

