package com.nihil.voice.postcall;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
public final class PostCallScheduler {
 private static final Logger log=LoggerFactory.getLogger(PostCallScheduler.class);private final PostCallProcessor processor;private final AtomicBoolean running=new AtomicBoolean();
 public PostCallScheduler(PostCallProcessor processor){this.processor=processor;}
 @Scheduled(fixedDelayString="${voice.post-call.poll-delay-ms:5000}") public void tick(){if(!running.compareAndSet(false,true))return;processor.runOnce().doFinally(signal->running.set(false)).subscribe(null,error->log.warn("Post-call worker failed",error));}
}
