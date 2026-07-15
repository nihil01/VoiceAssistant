package com.nihil.voice.asterisk;

import com.nihil.voice.call.CallSession;
import reactor.core.publisher.Mono;

public interface MediaGateway { Mono<MediaConnection> connect(CallSession call); }
