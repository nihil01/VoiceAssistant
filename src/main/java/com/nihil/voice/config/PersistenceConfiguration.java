package com.nihil.voice.config;

import com.nihil.voice.call.*;
import com.nihil.voice.events.*;
import com.nihil.voice.persistence.*;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.r2dbc.core.DatabaseClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods=false)
public class PersistenceConfiguration {
    @Bean ReactiveCallStore reactiveCallStore(DatabaseClient db){return new R2dbcCallStore(db);}
    @Bean ConversationMessageSink conversationMessageSink(DatabaseClient db,
        @Value("${voice.default-tenant-id:}") String tenant){return new R2dbcConversationMessageSink(db,uuid(tenant));}
    @Bean ReactiveEventOutbox reactiveEventOutbox(DatabaseClient db,ObjectMapper mapper){return new R2dbcEventOutbox(db,mapper);}
    @Bean @Primary CallLifecycleObserver callLifecycleObserver(ObjectProvider<VoiceCallCoordinator> coordinator,ReactiveCallStore store,
        @Value("${voice.default-tenant-id:}") String tenant,@Value("${voice.default-assistant-id:}") String assistant){
        return new PlatformCallLifecycleObserver(coordinator.getIfAvailable(),store,uuid(tenant),uuid(assistant));
    }
    private static UUID uuid(String value){return value==null||value.isBlank()?null:UUID.fromString(value);}
}
