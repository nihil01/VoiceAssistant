alter table call_summaries
    add column if not exists topic varchar(255);