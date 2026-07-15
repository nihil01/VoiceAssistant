package com.nihil.voice.call;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CallSessionTest {
    @Test
    void enforcesStateMachineAndTerminalIdempotency() {
        var session = CallSession.create(UUID.randomUUID(), "caller-1", "+994500000000", "700");
        assertThat(session.state()).isEqualTo(CallState.CREATED);
        session.transitionTo(CallState.ANSWERED);
        session.transitionTo(CallState.LISTENING);
        session.transitionTo(CallState.ENDING);
        assertThat(session.transitionTo(CallState.ENDED)).isTrue();
        assertThat(session.transitionTo(CallState.ENDED)).isFalse();
        assertThatThrownBy(() -> session.transitionTo(CallState.SPEAKING))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidatesOldTurnOnBargeIn() {
        var session = CallSession.create(UUID.randomUUID(), "caller-1", null, null);
        session.transitionTo(CallState.ANSWERED);
        session.transitionTo(CallState.LISTENING);
        UUID oldTurn = session.startTurn();
        session.transitionTo(CallState.THINKING);
        session.transitionTo(CallState.SPEAKING);
        UUID replacement = session.interruptAndStartListening();
        assertThat(replacement).isNotEqualTo(oldTurn);
        assertThat(session.isCurrentTurn(oldTurn)).isFalse();
        assertThat(session.state()).isEqualTo(CallState.LISTENING);
    }
}
