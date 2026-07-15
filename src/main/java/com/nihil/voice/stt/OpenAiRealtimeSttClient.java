package com.nihil.voice.stt;

import com.nihil.voice.audio.Pcm16Resampler;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;

import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import tools.jackson.databind.ObjectMapper;

public final class OpenAiRealtimeSttClient implements SttClient {
    private final WebSocketClient client; private final URI uri; private final HttpHeaders headers;
    private final ObjectMapper mapper; private final OpenAiSttEventParser parser; private final String model;
    private final String language; private final int sourceRate; private final int providerRate;
    public OpenAiRealtimeSttClient(ObjectMapper mapper,String url,String apiKey,String model,String language,int sourceRate,int providerRate){
        this(new ReactorNettyWebSocketClient(),mapper,url,apiKey,model,language,sourceRate,providerRate);
    }
    OpenAiRealtimeSttClient(WebSocketClient client,ObjectMapper mapper,String url,String apiKey,String model,String language,int sourceRate,int providerRate){
        this.client=client;this.mapper=mapper;this.parser=new OpenAiSttEventParser(mapper);this.uri=URI.create(url);this.model=model;
        this.language=language;this.sourceRate=sourceRate;this.providerRate=providerRate;this.headers=new HttpHeaders();
        this.headers.add(HttpHeaders.AUTHORIZATION,"Bearer "+apiKey);
    }
    @Override public Flux<SttEvent> transcribe(Flux<byte[]> audio){
        return Flux.create(sink->{
            Mono<Void> socket=client.execute(uri,headers,session->{
                Flux<org.springframework.web.reactive.socket.WebSocketMessage> messages=Flux.concat(
                    Mono.fromCallable(()->session.textMessage(sessionUpdate())),
                    audio.publishOn(Schedulers.parallel())
                        .map(bytes->sourceRate==providerRate?bytes:Pcm16Resampler.resampleMono(bytes,sourceRate,providerRate))
                        .map(bytes->session.textMessage(audioAppend(bytes)))
                );
                Mono<Void> send=session.send(messages);
                Mono<Void> receive=session.receive().filter(message->message.getType()==org.springframework.web.reactive.socket.WebSocketMessage.Type.TEXT)
                    .map(org.springframework.web.reactive.socket.WebSocketMessage::getPayloadAsText)
                    .flatMapIterable(raw->parser.parse(raw).stream().toList()).doOnNext(sink::next).then();
                return Mono.when(send,receive);
            }).retryWhen(Retry.backoff(2,Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(4)))
              .doOnSuccess(ignored->sink.complete()).doOnError(sink::error);
            Disposable subscription=socket.subscribe();
            sink.onCancel(subscription::dispose);sink.onDispose(subscription::dispose);
        });
    }

    private String sessionUpdate() {
        Map<String, Object> input = getInput();

        Map<String, Object> session = Map.of(
                "type", "transcription",
                "audio", Map.of(
                        "input", input
                )
        );

        return mapper.writeValueAsString(
                Map.of(
                        "type", "session.update",
                        "session", session
                )
        );
    }

    private @NonNull Map<String, Object> getInput() {
        var transcription = new java.util.LinkedHashMap<String, Object>();

        transcription.put("model", model);
        transcription.put("delay", "low");

        if (language != null && !language.isBlank()) {
            transcription.put("language", language);
        }

        return Map.of(
                "format", Map.of(
                        "type", "audio/pcm",
                        "rate", providerRate
                ),
                "transcription", transcription
        );
    }

    private String audioAppend(byte[] bytes){
        try{return mapper.writeValueAsString(Map.of("type","input_audio_buffer.append","audio",Base64.getEncoder().encodeToString(bytes)));}
        catch(Exception e){throw new IllegalArgumentException("Cannot encode STT audio",e);}
    }
}
