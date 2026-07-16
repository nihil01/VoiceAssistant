package com.nihil.voice.tts;

import com.nihil.voice.audio.Pcm16StreamResampler;
import java.time.Duration;
import java.util.Map;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

public final class OpenAiPcmTtsClient implements TtsClient {
    private final WebClient web; private final String model; private final String voice; private final int targetRate; private final Duration timeout;
    public OpenAiPcmTtsClient(WebClient.Builder builder,String baseUrl,String apiKey,String model,String voice,int targetRate,Duration timeout){
        this.web=builder.baseUrl(baseUrl).defaultHeader(HttpHeaders.AUTHORIZATION,"Bearer "+apiKey).build();
        this.model=model;this.voice=voice;this.targetRate=targetRate;this.timeout=timeout;
    }
    @Override public Flux<byte[]> synthesize(String text){
        Flux<byte[]> response=web.post().uri("/v1/audio/speech").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(Map.of("model",model,"voice",voice,"input",text,"response_format","pcm"))
            .retrieve().bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class)
            .map(buffer->{byte[] bytes=new byte[buffer.readableByteCount()];buffer.read(bytes);DataBufferUtils.release(buffer);return bytes;});
        return Flux.defer(()->{
            var resampler=new Pcm16StreamResampler(24000,targetRate);
            return response.publishOn(Schedulers.parallel())
                .map(resampler::append).filter(bytes->bytes.length>0)
                .concatWith(Flux.defer(()->Flux.just(resampler.finish())).filter(bytes->bytes.length>0));
        }).timeout(timeout);
    }
}
