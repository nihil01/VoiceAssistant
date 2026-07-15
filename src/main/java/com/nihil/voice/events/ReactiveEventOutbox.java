package com.nihil.voice.events;

import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ReactiveEventOutbox { Mono<UUID> append(PlatformEvent event); }
