package com.nihil.voice.events;

import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import tools.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

public final class R2dbcEventOutbox implements ReactiveEventOutbox {
    private final DatabaseClient db; private final ObjectMapper mapper;
    public R2dbcEventOutbox(DatabaseClient db, ObjectMapper mapper) { this.db = db; this.mapper = mapper; }
    @Override public Mono<UUID> append(PlatformEvent event) {
        UUID id = UUID.randomUUID();
        String json;
        try { json = mapper.writeValueAsString(event.payload()); }
        catch (Exception e) { return Mono.error(new IllegalArgumentException("Event payload is not JSON serializable", e)); }
        var spec = db.sql("insert into event_outbox(id, tenant_id, aggregate_id, event_type, payload, created_at) values(:id,:tenant,:aggregate,:type,cast(:payload as jsonb),:created)")
            .bind("id", id).bind("aggregate", event.aggregateId()).bind("type", event.type()).bind("payload", json).bind("created", event.occurredAt());
        spec = event.tenantId() == null ? spec.bindNull("tenant", UUID.class) : spec.bind("tenant", event.tenantId());
        return spec.fetch().rowsUpdated().thenReturn(id);
    }
}
