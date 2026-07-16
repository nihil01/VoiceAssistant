package com.nihil.voice.tts;

import com.nihil.voice.audio.Pcm16StreamResampler;
import com.nihil.voice.audio.PcmFramePacketizer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

public final class OpenAiPcmTtsClient implements TtsClient {

    private static final int OPENAI_PCM_RATE = 24_000;

    private final WebClient web;
    private final String model;
    private final String voice;
    private final String instructions;
    private final int targetRate;
    private final Duration timeout;

    public OpenAiPcmTtsClient(
            WebClient.Builder builder,
            String baseUrl,
            String apiKey,
            String model,
            String voice,
            String instructions,
            int targetRate,
            Duration timeout
    ) {
        this.web = builder
                .baseUrl(baseUrl)
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + apiKey
                )
                .build();

        this.model = model;
        this.voice = voice;
        this.instructions = instructions;
        this.targetRate = targetRate;
        this.timeout = timeout;
    }

    @Override
    public Flux<byte[]> synthesize(String text) {
        if (text == null || text.isBlank()) {
            return Flux.empty();
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("voice", voice);
        request.put("input", text);
        request.put("response_format", "pcm");

        if (instructions != null && !instructions.isBlank()) {
            request.put("instructions", instructions);
        }

        Flux<byte[]> response = web.post()
                .uri("/v1/audio/speech")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(
                        org.springframework.core.io.buffer.DataBuffer.class
                )
                .map(buffer -> {
                    try {
                        byte[] bytes =
                                new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                });

        return Flux.defer(() -> {
            var resampler = new Pcm16StreamResampler(OPENAI_PCM_RATE, targetRate);
            var packetizer = new PcmFramePacketizer(targetRate, 20, 4);

            Flux<byte[]> audioFrames = response
                    .concatMap(chunk -> Flux.fromIterable(packetizer.append(resampler.append(chunk))));

            Flux<byte[]> tail = Flux.defer(() -> {
                var frames = new java.util.ArrayList<byte[]>();
                byte[] remaining = resampler.finish();
                if (remaining.length > 0) {
                    frames.addAll(packetizer.append(remaining));
                }
                frames.addAll(packetizer.finish());
                return Flux.fromIterable(frames);
            });

            return audioFrames.concatWith(tail);
        }).timeout(timeout);
    }
}
