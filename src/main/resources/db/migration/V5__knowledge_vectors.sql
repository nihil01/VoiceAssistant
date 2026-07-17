create extension if not exists vector;

insert into tenants(id, name, timezone)
values ('00000000-0000-0000-0000-000000000001', 'Default workspace', 'Asia/Baku')
on conflict (id) do nothing;

alter table knowledge_documents
    add column if not exists embedding vector(1536),
    add column if not exists source varchar(32) not null default 'LOCAL',
    add column if not exists external_id varchar(255),
    add column if not exists category varchar(255),
    add column if not exists source_url text,
    add column if not exists active boolean not null default true,
    add column if not exists content_hash varchar(64),
    add column if not exists remote_updated_at timestamptz,
    add column if not exists synced_at timestamptz;

create unique index if not exists uq_knowledge_source_external
    on knowledge_documents(tenant_id, source, external_id)
    where external_id is not null;

create index if not exists idx_knowledge_active
    on knowledge_documents(tenant_id, active);

create index if not exists idx_knowledge_embedding_hnsw
    on knowledge_documents using hnsw (embedding vector_cosine_ops)
    where active = true and embedding is not null;