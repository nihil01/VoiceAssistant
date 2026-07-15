create table post_call_jobs (
    call_id uuid primary key references calls(id) on delete cascade,
    status varchar(24) not null default 'PENDING',
    attempts integer not null default 0,
    next_attempt_at timestamptz not null default now(),
    last_error text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index post_call_jobs_pending_idx on post_call_jobs(next_attempt_at) where status in ('PENDING','RETRY');
