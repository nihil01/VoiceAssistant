package com.nihil.voice.config;

import com.nihil.voice.asterisk.*;
import com.nihil.voice.call.CallSessionManager;
import com.nihil.voice.call.CallLifecycleObserver;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AsteriskProperties.class)
public class AsteriskConfiguration {
    @Bean CallSessionManager callSessionManager() { return new CallSessionManager(); }
    @Bean AriEventParser ariEventParser(ObjectMapper mapper) { return new AriEventParser(mapper); }

    @Bean
    @ConditionalOnProperty(prefix = "voice.asterisk", name = "enabled", havingValue = "true")
    AriGateway ariGateway(WebClient.Builder builder, AsteriskProperties properties) {
        return new WebClientAriGateway(builder, properties);
    }
    @Bean
    @ConditionalOnProperty(prefix = "voice.asterisk", name = "enabled", havingValue = "true")
    MediaGateway mediaGateway(AsteriskProperties properties, MeterRegistry meters) {
        return new ReactorNettyMediaGateway(properties, meters);
    }
    @Bean
    @ConditionalOnProperty(prefix = "voice.asterisk", name = "enabled", havingValue = "true")
    AsteriskBridgeService bridgeService(AriGateway ari, MediaGateway media, CallSessionManager calls,
                                        AsteriskProperties properties,
                                        org.springframework.beans.factory.ObjectProvider<CallLifecycleObserver> lifecycle) {
        return new AsteriskBridgeService(ari, media, calls,
            lifecycle.getIfAvailable(() -> CallLifecycleObserver.NOOP), properties.requestTimeout());
    }
    @Bean
    @ConditionalOnProperty(prefix = "voice.asterisk", name = "enabled", havingValue = "true")
    AriEventStream ariEventStream(AsteriskProperties properties, AriEventParser parser) {
        return new AriEventStream(properties, parser);
    }
    @Bean
    @ConditionalOnProperty(prefix = "voice.asterisk", name = "enabled", havingValue = "true")
    AriEventHandler ariEventHandler(AsteriskBridgeService bridges) { return new AriEventHandler(bridges); }

    @Bean
    @ConditionalOnProperty(prefix = "voice.asterisk", name = "enabled", havingValue = "true")
    AriListenerLifecycle ariListenerLifecycle(AriEventStream stream, AriEventHandler handler,
                                              AsteriskProperties properties) {
        AsteriskStartupValidator.validate(properties);
        return new AriListenerLifecycle(new AriEventListener(stream, handler));
    }

    static final class AriListenerLifecycle implements InitializingBean, DisposableBean {
        private final AriEventListener listener;
        AriListenerLifecycle(AriEventListener listener) { this.listener = listener; }
        public void afterPropertiesSet() { listener.start(); }
        public void destroy() { listener.stop(); }
    }
}
