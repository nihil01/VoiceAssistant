package com.nihil.voice.postcall;

import static org.assertj.core.api.Assertions.assertThat;
import com.nihil.voice.llm.LlmClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class LlmCallSummaryProviderTest {
    @Test void parsesStructuredSummaryAcrossStreamingChunks() {
        LlmClient llm = messages -> Flux.just(
            "{\"shortSummary\":\"Клиент спросил цену\",\"fullSummary\":\"Обсудили тариф\",",
            "\"intent\":\"pricing\",\"sentiment\":\"positive\",\"leadQuality\":\"hot\",",
            "\"outcome\":\"follow_up\",\"nextAction\":\"Позвонить завтра\",\"extractedData\":{\"budget\":1000}}"
        );
        var provider = new LlmCallSummaryProvider(llm, new ObjectMapper());
        StepVerifier.create(provider.summarize(new PostCallCandidate(java.util.UUID.randomUUID(), null, "user: цена?")))
            .assertNext(summary -> {
                assertThat(summary.intent()).isEqualTo("pricing");
                assertThat(summary.extractedData()).containsEntry("budget", 1000);
            }).verifyComplete();
    }

    @Test void rejectsMalformedProviderOutput() {
        var provider = new LlmCallSummaryProvider(messages -> Flux.just("not-json"), new ObjectMapper());
        StepVerifier.create(provider.summarize(new PostCallCandidate(java.util.UUID.randomUUID(), null, "x")))
            .expectError(IllegalArgumentException.class).verify();
    }
}
