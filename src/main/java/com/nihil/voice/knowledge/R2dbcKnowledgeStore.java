package com.nihil.voice.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class R2dbcKnowledgeStore implements KnowledgeSyncStore {
    private final DatabaseClient db;
    private final TransactionalOperator transactions;
    private final UUID tenantId;
    private final double maximumDistance;

    public R2dbcKnowledgeStore(DatabaseClient db, TransactionalOperator transactions, UUID tenantId, double maximumDistance) {
        this.db = db;
        this.transactions = transactions;
        this.tenantId = tenantId;
        this.maximumDistance = maximumDistance;
    }

    @Override
    public Flux<KnowledgeSnippet> searchByVector(float[] embedding, int limit) {
        return db.sql("""
                        select title, content, category
                        from knowledge_documents
                        where tenant_id = :tenant and active = true and embedding is not null
                          and (embedding <=> cast(:embedding as vector)) <= :distance
                        order by embedding <=> cast(:embedding as vector)
                        limit :limit
                        """)
                .bind("tenant", tenantId)
                .bind("embedding", vectorLiteral(embedding))
                .bind("distance", maximumDistance)
                .bind("limit", Math.max(1, limit))
                .map((row, metadata) -> new KnowledgeSnippet(row.get("title", String.class), row.get("content", String.class), row.get("category", String.class)))
                .all();
    }

    @Override
    public Flux<KnowledgeSnippet> searchByText(String query, int limit) {
        return db.sql("""
                        select title, content, category
                        from knowledge_documents
                        where tenant_id = :tenant and active = true
                          and to_tsvector('simple', coalesce(title, '') || ' ' || content)
                              @@ plainto_tsquery('simple', :query)
                        order by ts_rank(to_tsvector('simple', coalesce(title, '') || ' ' || content), plainto_tsquery('simple', :query)) desc
                        limit :limit
                        """)
                .bind("tenant", tenantId)
                .bind("query", query)
                .bind("limit", Math.max(1, limit))
                .map((row, metadata) -> new KnowledgeSnippet(row.get("title", String.class), row.get("content", String.class), row.get("category", String.class)))
                .all();
    }

    @Override
    public Mono<Boolean> hasContentHash(String remoteId, String hash) {
        return db.sql("""
                        select exists(select 1 from knowledge_documents
                            where tenant_id = :tenant and source = 'TWENTY'
                              and external_id = :external and content_hash = :hash
                              and (active = false or embedding is not null)) as present
                        """)
                .bind("tenant", tenantId).bind("external", remoteId).bind("hash", hash)
                .map((row, metadata) -> Boolean.TRUE.equals(row.get("present", Boolean.class)))
                .one().defaultIfEmpty(false);
    }

    @Override
    public Mono<Void> replaceTwentySnapshot(List<IndexedKnowledgeEntry> entries) {
        Mono<Void> update = db.sql("""
                        update knowledge_documents set active = false, synced_at = now(), updated_at = now()
                        where tenant_id = :tenant and source = 'TWENTY'
                        """)
                .bind("tenant", tenantId).fetch().rowsUpdated().then()
                .thenMany(Flux.fromIterable(entries).concatMap(this::upsert)).then();
        return transactions.transactional(update);
    }

    private Mono<Void> upsert(IndexedKnowledgeEntry indexed) {
        RemoteKnowledgeEntry entry = indexed.source();
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into knowledge_documents(
                            id, tenant_id, title, content, embedding, source, external_id,
                            category, source_url, active, content_hash, remote_updated_at,
                            synced_at, created_at, updated_at
                        ) values (
                            :id, :tenant, :title, :content, cast(:embedding as vector), 'TWENTY', :external,
                            :category, :sourceUrl, :active, :hash, :remoteUpdatedAt, now(), now(), now()
                        )
                        on conflict (tenant_id, source, external_id) where external_id is not null
                        do update set title = excluded.title, content = excluded.content,
                            embedding = case when :preserveEmbedding then knowledge_documents.embedding else excluded.embedding end,
                            category = excluded.category, source_url = excluded.source_url, active = excluded.active,
                            content_hash = excluded.content_hash, remote_updated_at = excluded.remote_updated_at,
                            synced_at = now(), updated_at = now()
                        """)
                .bind("id", UUID.randomUUID()).bind("tenant", tenantId)
                .bind("title", entry.title()).bind("content", entry.content())
                .bind("external", entry.remoteId()).bind("active", entry.active())
                .bind("hash", indexed.contentHash()).bind("preserveEmbedding", indexed.preserveExistingEmbedding());
        spec = bindNullable(spec, "embedding", indexed.embedding() == null ? null : vectorLiteral(indexed.embedding()), String.class);
        spec = bindNullable(spec, "category", entry.category(), String.class);
        spec = bindNullable(spec, "sourceUrl", entry.sourceUrl(), String.class);
        spec = bindNullable(spec, "remoteUpdatedAt", entry.updatedAt(), Instant.class);
        return spec.fetch().rowsUpdated().then();
    }

    static String vectorLiteral(float[] vector) {
        if (vector == null || vector.length == 0) throw new IllegalArgumentException("Embedding vector is empty");
        StringBuilder value = new StringBuilder(vector.length * 8).append('[');
        for (int index = 0; index < vector.length; index++) {
            float item = vector[index];
            if (!Float.isFinite(item)) throw new IllegalArgumentException("Embedding contains a non-finite value");
            if (index > 0) value.append(',');
            value.append(Float.toString(item));
        }
        return value.append(']').toString();
    }

    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }
}