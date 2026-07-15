package com.nihil.voice.config;

import com.nihil.voice.call.*;
import com.nihil.voice.conversation.ConversationService;
import com.nihil.voice.conversation.TurnCancellationRegistry;
import com.nihil.voice.stt.SttClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
public class VoicePipelineConfiguration {
    @Bean @ConditionalOnBean({SttClient.class,ConversationService.class})
    VoiceCallCoordinator voiceCallCoordinator(SttClient stt,ConversationService conversation,TurnCancellationRegistry cancellations){return new VoiceCallCoordinator(stt,conversation,cancellations);}
}
