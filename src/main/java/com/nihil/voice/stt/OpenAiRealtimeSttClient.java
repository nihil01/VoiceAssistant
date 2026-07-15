package com.nihil.voice.stt;

import com.nihil.voice.audio.Pcm16Resampler;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

public final class OpenAiRealtimeSttClient implements SttClient {
    private static final Logger log=LoggerFactory.getLogger(OpenAiRealtimeSttClient.class);
    private final WebSocketClient client;
    private final URI uri;
    private final HttpHeaders headers;
    private final ObjectMapper mapper;
    private final OpenAiSttEventParser parser;
    private final String model;
    private final String language;
    private final int sourceRate;
    private final int providerRate;
    private final double vadRmsThreshold;
    private final Duration minimumSpeech;
    private final Duration endSilence;
    private final Duration maximumUtterance;

    public OpenAiRealtimeSttClient(ObjectMapper mapper,String url,String apiKey,String model,String language,int sourceRate,int providerRate){
        this(mapper,url,apiKey,model,language,sourceRate,providerRate,400,Duration.ofMillis(100),Duration.ofMillis(600),Duration.ofSeconds(15));
    }

    public OpenAiRealtimeSttClient(ObjectMapper mapper,String url,String apiKey,String model,String language,int sourceRate,int providerRate,
                                   double vadRmsThreshold,Duration minimumSpeech,Duration endSilence,Duration maximumUtterance){
        this(new ReactorNettyWebSocketClient(),mapper,url,apiKey,model,language,sourceRate,providerRate,
            vadRmsThreshold,minimumSpeech,endSilence,maximumUtterance);
    }

    OpenAiRealtimeSttClient(WebSocketClient client,ObjectMapper mapper,String url,String apiKey,String model,String language,
                            int sourceRate,int providerRate,double vadRmsThreshold,Duration minimumSpeech,
                            Duration endSilence,Duration maximumUtterance){
        this.client=client;this.mapper=mapper;this.parser=new OpenAiSttEventParser(mapper);this.uri=URI.create(url);this.model=model;
        this.language=language;this.sourceRate=sourceRate;this.providerRate=providerRate;this.vadRmsThreshold=vadRmsThreshold;
        this.minimumSpeech=minimumSpeech;this.endSilence=endSilence;this.maximumUtterance=maximumUtterance;
        this.headers=new HttpHeaders();this.headers.add(HttpHeaders.AUTHORIZATION,"Bearer "+apiKey);
    }

    @Override public Flux<SttEvent> transcribe(Flux<byte[]> audio){
        return Flux.create(sink->{
            PcmTurnDetector turns=new PcmTurnDetector(sourceRate,vadRmsThreshold,minimumSpeech.toMillis(),endSilence.toMillis(),maximumUtterance.toMillis());
            AtomicLong frames=new AtomicLong();
            AtomicLong commits=new AtomicLong();
            Mono<Void> socket=client.execute(uri,headers,session->{
                log.info("OpenAI Realtime STT WebSocket connected model={} sourceRate={} providerRate={}",model,sourceRate,providerRate);
                Flux<WebSocketMessage> messages=Flux.concat(
                    Mono.fromCallable(()->{
                        log.info("Sending OpenAI transcription session.update model={}",model);
                        return session.textMessage(sessionUpdate());
                    }),
                    audio.publishOn(Schedulers.parallel()).concatMap(source->{
                        boolean commit=turns.accept(source);
                        byte[] provider=sourceRate==providerRate?source:Pcm16Resampler.resampleMono(source,sourceRate,providerRate);
                        var output=new ArrayList<WebSocketMessage>(2);
                        output.add(session.textMessage(audioAppend(provider)));
                        long frame=frames.incrementAndGet();
                        if(frame==1)log.info("First audio frame appended to OpenAI STT bytes={}",provider.length);
                        if(commit){
                            long number=commits.incrementAndGet();
                            log.info("Committing OpenAI STT input buffer utterance={}",number);
                            output.add(session.textMessage(audioCommit()));
                        }
                        return Flux.fromIterable(output);
                    },1)
                );
                Mono<Void> send=session.send(messages);
                Mono<Void> receive=session.receive()
                    .filter(message->message.getType()==WebSocketMessage.Type.TEXT)
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext(this::logProviderEvent)
                    .flatMapIterable(raw->parser.parse(raw).stream().toList())
                    .doOnNext(sink::next)
                    .then();
                return Mono.when(send,receive);
            }).doOnSubscribe(ignored->log.info("Connecting OpenAI Realtime STT WebSocket model={}",model))
              .doOnSuccess(ignored->{
                  log.info("OpenAI Realtime STT WebSocket closed model={} frames={} commits={}",model,frames.get(),commits.get());
                  sink.complete();
              }).doOnError(error->{
                  log.error("OpenAI Realtime STT WebSocket failed model={}",model,error);
                  sink.error(error);
              });
            Disposable subscription=socket.subscribe();
            sink.onCancel(subscription::dispose);
            sink.onDispose(subscription::dispose);
        });
    }

    private String sessionUpdate(){
        Map<String,Object> input=getInput();
        Map<String,Object> session=Map.of("type","transcription","audio",Map.of("input",input));
        return mapper.writeValueAsString(Map.of("type","session.update","session",session));
    }

    private @NonNull Map<String,Object> getInput(){
        var transcription=new java.util.LinkedHashMap<String,Object>();
        transcription.put("model",model);
        transcription.put("delay","low");
        if(language!=null&&!language.isBlank())transcription.put("language",language);
        return Map.of("format",Map.of("type","audio/pcm","rate",providerRate),"transcription",transcription);
    }

    private String audioAppend(byte[] bytes){
        return mapper.writeValueAsString(Map.of("type","input_audio_buffer.append","audio",Base64.getEncoder().encodeToString(bytes)));
    }

    private String audioCommit(){return mapper.writeValueAsString(Map.of("type","input_audio_buffer.commit"));}

    private void logProviderEvent(String raw){
        try{
            String type=mapper.readTree(raw).path("type").asText("unknown");
            if("session.created".equals(type)||"session.updated".equals(type)||"error".equals(type))
                log.info("OpenAI STT event type={}",type);
            else log.debug("OpenAI STT event type={}",type);
        }catch(Exception ignored){log.warn("Received malformed OpenAI STT event");}
    }
}
