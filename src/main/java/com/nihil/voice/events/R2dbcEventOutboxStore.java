package com.nihil.voice.events;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
public final class R2dbcEventOutboxStore implements EventOutboxStore {
 private final DatabaseClient db;public R2dbcEventOutboxStore(DatabaseClient db){this.db=db;}
 public Flux<OutboxMessage> pending(int limit){return db.sql("select id,event_type,payload::text payload from event_outbox where published_at is null order by created_at limit :limit").bind("limit",limit).map((r,m)->new OutboxMessage(r.get("id",UUID.class),r.get("event_type",String.class),r.get("payload",String.class))).all();}
 public Mono<Void> markPublished(UUID id){return db.sql("update event_outbox set published_at=now() where id=:id and published_at is null").bind("id",id).fetch().rowsUpdated().then();}
}
