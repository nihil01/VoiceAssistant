create extension if not exists pgcrypto;

create table tenants (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    default_language varchar(16) not null default 'az',
    timezone varchar(64) not null default 'Asia/Baku',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create table ai_assistants (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    name text not null,
    enabled boolean not null default true,
    voice varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create table assistant_prompts (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    assistant_id uuid not null references ai_assistants(id),
    version integer not null,
    system_prompt text not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    unique (assistant_id, version)
);
create table phone_numbers (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    assistant_id uuid not null references ai_assistants(id),
    e164 varchar(32) not null unique,
    enabled boolean not null default true,
    created_at timestamptz not null default now()
);
create table calls (
    id uuid primary key,
    tenant_id uuid references tenants(id),
    assistant_id uuid references ai_assistants(id),
    external_call_id varchar(255) not null unique,
    asterisk_channel_id varchar(255) not null,
    media_channel_id varchar(255),
    bridge_id varchar(255),
    direction varchar(16) not null default 'inbound',
    caller_number varchar(64),
    destination_number varchar(64),
    status varchar(32) not null,
    started_at timestamptz not null,
    answered_at timestamptz,
    ended_at timestamptz,
    duration_seconds bigint,
    hangup_cause varchar(128),
    recording_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index calls_tenant_started_idx on calls(tenant_id, started_at desc);
create table call_messages (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid references tenants(id),
    call_id uuid not null references calls(id) on delete cascade,
    turn_id uuid,
    provider_event_id varchar(255),
    role varchar(16) not null,
    text text not null,
    is_final boolean not null default true,
    started_at timestamptz,
    ended_at timestamptz,
    sequence_number bigint not null,
    stt_confidence numeric(6,5),
    created_at timestamptz not null default now(),
    unique(call_id, sequence_number),
    unique(call_id, provider_event_id)
);
create table call_events (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid references tenants(id),
    call_id uuid not null references calls(id) on delete cascade,
    external_event_id varchar(255),
    event_type varchar(64) not null,
    payload jsonb not null default '{}'::jsonb,
    occurred_at timestamptz not null,
    created_at timestamptz not null default now(),
    unique(call_id, external_event_id)
);
create table call_summaries (
    call_id uuid primary key references calls(id) on delete cascade,
    short_summary text,
    full_summary text,
    intent varchar(64), sentiment varchar(32), lead_quality varchar(32), outcome varchar(64), next_action text,
    extracted_data_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
create table call_costs (
    call_id uuid primary key references calls(id) on delete cascade,
    stt_seconds numeric(14,3) not null default 0, stt_cost numeric(14,6) not null default 0,
    llm_input_tokens bigint not null default 0, llm_output_tokens bigint not null default 0, llm_cost numeric(14,6) not null default 0,
    tts_characters bigint not null default 0, tts_seconds numeric(14,3) not null default 0, tts_cost numeric(14,6) not null default 0,
    total_cost numeric(14,6) not null default 0, currency varchar(8) not null default 'USD'
);
create table knowledge_documents (
    id uuid primary key default gen_random_uuid(), tenant_id uuid not null references tenants(id),
    title text not null, content text not null, search_vector tsvector generated always as (to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(content,''))) stored,
    created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create index knowledge_documents_search_idx on knowledge_documents using gin(search_vector);
create table crm_sync_jobs (
    id uuid primary key default gen_random_uuid(), tenant_id uuid references tenants(id), call_id uuid not null references calls(id) on delete cascade,
    external_call_id varchar(255) not null unique, status varchar(32) not null default 'PENDING', attempts integer not null default 0,
    next_attempt_at timestamptz not null default now(), locked_at timestamptz, last_error text, remote_ids jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table event_outbox (
    id uuid primary key default gen_random_uuid(), tenant_id uuid, aggregate_id uuid not null, event_type varchar(64) not null,
    payload jsonb not null, created_at timestamptz not null default now(), published_at timestamptz
);
create index event_outbox_pending_idx on event_outbox(created_at) where published_at is null;
