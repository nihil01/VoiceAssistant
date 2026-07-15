package com.nihil.voice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("voice.providers.openai")
public record OpenAiProperties(boolean enabled,String apiKey,String baseUrl,String sttUrl,String sttModel,String sttLanguage,
                               int sttSourceRate,int sttProviderRate,String llmModel,String ttsModel,String ttsVoice,
                               Duration llmTimeout,Duration ttsTimeout) {
    public OpenAiProperties {
        baseUrl=blank(baseUrl,"https://api.openai.com");sttModel=blank(sttModel,"gpt-realtime-whisper");llmModel=blank(llmModel,"gpt-4.1-mini");
        ttsModel=blank(ttsModel,"gpt-4o-mini-tts");ttsVoice=blank(ttsVoice,"alloy");
        sttSourceRate=sttSourceRate<=0?16000:sttSourceRate;sttProviderRate=sttProviderRate<=0?24000:sttProviderRate;
        llmTimeout=llmTimeout==null?Duration.ofSeconds(20):llmTimeout;ttsTimeout=ttsTimeout==null?Duration.ofSeconds(20):ttsTimeout;
    }
    private static String blank(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
}
