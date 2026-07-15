package com.nihil.voice.events;
import java.util.Map;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
public final class RedisStreamEventTransport implements DomainEventTransport {
 private final ReactiveStringRedisTemplate redis;private final String stream;
 public RedisStreamEventTransport(ReactiveStringRedisTemplate redis,String stream){this.redis=redis;this.stream=stream;}
 public Mono<String> publish(OutboxMessage event){
   var record=StreamRecords.string(Map.of("eventId",event.id().toString(),"eventType",event.eventType(),"payload",event.payload())).withStreamKey(stream);
   return redis.opsForStream().add(record).map(Object::toString);
 }
}
