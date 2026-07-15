package com.nihil.voice.llm;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

public final class OpenAiResponsesLlmClient implements LlmClient {
    private final WebClient web; private final String model; private final Duration timeout; private final OpenAiLlmEventParser parser;
    public OpenAiResponsesLlmClient(WebClient.Builder builder, ObjectMapper mapper, String baseUrl, String apiKey, String model, Duration timeout) {
        this.web=builder.baseUrl(baseUrl).defaultHeader(HttpHeaders.AUTHORIZATION,"Bearer "+apiKey).build();
        this.model=model;this.timeout=timeout;this.parser=new OpenAiLlmEventParser(mapper);
    }
    @Override public Flux<String> stream(List<ConversationMessage> messages) {
        List<Map<String,String>> input=messages.stream().map(message->Map.of("role",message.role().name().toLowerCase(),"content",message.content())).toList();
        return web.post().uri("/v1/responses").contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue(Map.of("model",model,"input",input,"stream",true))
            .retrieve().bodyToFlux(String.class).flatMapIterable(raw->parser.textDelta(raw).stream().toList()).timeout(timeout);
    }
}
