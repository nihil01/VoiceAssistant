alter table calls
    add column if not exists twenty_record_id uuid;

alter table assistant_prompts
    add column if not exists external_prompt_id varchar(255),
    add column if not exists language varchar(16),
    add column if not exists tts_voice varchar(128),
    add column if not exists tts_instructions text,
    add column if not exists twenty_record_id uuid;

create unique index if not exists assistant_prompts_external_prompt_idx
    on assistant_prompts(external_prompt_id)
    where external_prompt_id is not null;

create table call_recordings (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid references tenants(id),
    call_id uuid not null references calls(id) on delete cascade,
    storage_provider varchar(32) not null default 'local',
    storage_key text,
    recording_url text,
    mime_type varchar(128) not null default 'audio/wav',
    size_bytes bigint,
    duration_seconds numeric(14,3),
    checksum_sha256 varchar(64),
    status varchar(32) not null default 'AVAILABLE',
    twenty_record_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(call_id, storage_key)
);

create index call_recordings_call_idx on call_recordings(call_id);
create index call_recordings_tenant_created_idx on call_recordings(tenant_id, created_at desc);
