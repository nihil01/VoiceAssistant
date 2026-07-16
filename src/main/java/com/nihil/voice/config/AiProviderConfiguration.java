package com.nihil.voice.config;

import com.nihil.voice.call.ConversationMessageSink;
import com.nihil.voice.conversation.ConversationService;
import com.nihil.voice.conversation.TurnCancellationRegistry;
import com.nihil.voice.llm.LlmClient;
import com.nihil.voice.llm.OpenAiResponsesLlmClient;
import com.nihil.voice.stt.OpenAiRealtimeSttClient;
import com.nihil.voice.stt.SttClient;
import com.nihil.voice.tts.OpenAiPcmTtsClient;
import com.nihil.voice.tts.TtsClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenAiProperties.class)
@ConditionalOnProperty(
        prefix = "voice.providers.openai",
        name = "enabled",
        havingValue = "true"
)
public class AiProviderConfiguration {
    @Bean
    SttClient sttClient(ObjectMapper mapper, OpenAiProperties properties) {
        return new OpenAiRealtimeSttClient(
                mapper,
                properties.sttUrl(),
                properties.apiKey(),
                properties.sttModel(),
                properties.sttLanguage(),
                properties.sttSourceRate(),
                properties.sttProviderRate(),
                properties.sttVadRmsThreshold(),
                properties.sttMinimumSpeech(),
                properties.sttEndSilence(),
                properties.sttMaximumUtterance()
        );
    }

    @Bean
    LlmClient llmClient(
            WebClient.Builder builder,
            ObjectMapper mapper,
            OpenAiProperties properties
    ) {
        return new OpenAiResponsesLlmClient(
                builder,
                mapper,
                properties.baseUrl(),
                properties.apiKey(),
                properties.llmModel(),
                properties.llmTimeout(),
                properties.llmSystemPrompt()
        );
    }

    @Bean
    TtsClient ttsClient(
            WebClient.Builder builder,
            OpenAiProperties properties
    ) {
        return new OpenAiPcmTtsClient(
                builder,
                properties.baseUrl(),
                properties.apiKey(),
                properties.ttsModel(),
                properties.ttsVoice(),
                properties.ttsInstructions(),
                properties.sttSourceRate(),
                properties.ttsTimeout()
        );
    }

    @Bean
    TurnCancellationRegistry turnCancellationRegistry() {
        return new TurnCancellationRegistry();
    }

    @Bean
    ConversationService conversationService(
            LlmClient llm,
            TtsClient tts,
            TurnCancellationRegistry cancellations,
            ConversationMessageSink messages
    ) {
        return new ConversationService(llm, tts, cancellations, messages);
    }
}
