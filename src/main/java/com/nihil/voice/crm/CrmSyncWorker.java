package com.nihil.voice.crm;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

public final class CrmSyncWorker {
    private static final Logger log=LoggerFactory.getLogger(CrmSyncWorker.class);
    private final CrmSyncJobStore jobs;private final CrmSyncService sync;private final int batchSize;private final AtomicBoolean running=new AtomicBoolean();
    public CrmSyncWorker(CrmSyncJobStore jobs,CrmSyncService sync,int batchSize){this.jobs=jobs;this.sync=sync;this.batchSize=batchSize;}
    public Flux<String> runOnce(){return jobs.claim(batchSize).flatMap(job->sync.sync(job.call()).flatMap(remote->jobs.complete(job.jobId(),remote).thenReturn(remote))
        .onErrorResume(error->jobs.fail(job.jobId(),job.attempts(),error.getMessage()).thenReturn("failed:"+job.jobId())),4);}
    public void scheduledRun(){if(!running.compareAndSet(false,true))return;runOnce().doOnError(error->log.error("CRM sync batch failed",error)).doFinally(ignored->running.set(false)).subscribe();}
}
