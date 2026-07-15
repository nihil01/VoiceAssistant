package com.nihil.voice.config;

import com.nihil.voice.call.*;
import com.nihil.voice.conversation.ConversationService;
import com.nihil.voice.conversation.TurnCancellationRegistry;
import com.nihil.voice.stt.SttClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
public class VoicePipelineConfiguration {
    @Bean @ConditionalOnProperty(prefix="voice.providers.openai",name="enabled",havingValue="true")
    VoiceCallCoordinator voiceCallCoordinator(SttClient stt,ConversationService conversation,TurnCancellationRegistry cancellations,
                                              @Value("${voice.logging.transcripts:false}") boolean logTranscripts){
        return new VoiceCallCoordinator(stt,conversation,cancellations,logTranscripts);
    }
}
