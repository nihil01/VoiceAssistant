package com.nihil.voice.config;

import com.nihil.voice.conversation.ConversationService;
import com.nihil.voice.conversation.TurnCancellationRegistry;
import com.nihil.voice.call.ConversationMessageSink;
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

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(OpenAiProperties.class)
@ConditionalOnProperty(prefix="voice.providers.openai",name="enabled",havingValue="true")
public class AiProviderConfiguration {
    @Bean SttClient sttClient(ObjectMapper mapper,OpenAiProperties p){return new OpenAiRealtimeSttClient(mapper,p.sttUrl(),p.apiKey(),p.sttModel(),p.sttLanguage(),p.sttSourceRate(),p.sttProviderRate());}
    @Bean LlmClient llmClient(WebClient.Builder builder,ObjectMapper mapper,OpenAiProperties p){return new OpenAiResponsesLlmClient(builder,mapper,p.baseUrl(),p.apiKey(),p.llmModel(),p.llmTimeout());}
    @Bean TtsClient ttsClient(WebClient.Builder builder,OpenAiProperties p){return new OpenAiPcmTtsClient(builder,p.baseUrl(),p.apiKey(),p.ttsModel(),p.ttsVoice(),p.sttSourceRate(),p.ttsTimeout());}
    @Bean TurnCancellationRegistry turnCancellationRegistry(){return new TurnCancellationRegistry();}
    @Bean ConversationService conversationService(LlmClient llm,TtsClient tts,TurnCancellationRegistry cancellations,ConversationMessageSink messages){return new ConversationService(llm,tts,cancellations,messages);}
}
