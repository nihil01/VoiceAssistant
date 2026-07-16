package com.nihil.voice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("voice.providers.openai")
public record OpenAiProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String sttUrl,
        String sttModel,
        String sttLanguage,
        int sttSourceRate,
        int sttProviderRate,
        double sttVadRmsThreshold,
        Duration sttMinimumSpeech,
        Duration sttEndSilence,
        Duration sttMaximumUtterance,
        String llmModel,
        String ttsModel,
        String ttsVoice,
        Duration llmTimeout,
        Duration ttsTimeout,
        String llmSystemPrompt,
        String ttsInstructions
) {
    public OpenAiProperties {
        baseUrl = defaultString(baseUrl, "https://api.openai.com");
        sttModel = defaultString(sttModel, "gpt-realtime-whisper");
        llmModel = defaultString(llmModel, "gpt-4.1-mini");
        ttsModel = defaultString(ttsModel, "gpt-4o-mini-tts");
        ttsVoice = defaultString(ttsVoice, "alloy");

        sttSourceRate = sttSourceRate <= 0 ? 16_000 : sttSourceRate;
        sttProviderRate = sttProviderRate <= 0 ? 24_000 : sttProviderRate;
        sttVadRmsThreshold = sttVadRmsThreshold <= 0 ? 400 : sttVadRmsThreshold;

        sttMinimumSpeech = defaultDuration(
                sttMinimumSpeech,
                Duration.ofMillis(100)
        );
        sttEndSilence = defaultDuration(
                sttEndSilence,
                Duration.ofMillis(600)
        );
        sttMaximumUtterance = defaultDuration(
                sttMaximumUtterance,
                Duration.ofSeconds(15)
        );
        llmTimeout = defaultDuration(llmTimeout, Duration.ofSeconds(20));
        ttsTimeout = defaultDuration(ttsTimeout, Duration.ofSeconds(20));
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Duration defaultDuration(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }
}
