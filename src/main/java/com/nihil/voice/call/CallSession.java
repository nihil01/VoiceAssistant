package com.nihil.voice.call;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class CallSession {
    private static final Map<CallState, EnumSet<CallState>> TRANSITIONS = transitions();

    private final UUID internalCallId;
    private final String asteriskChannelId;
    private final String callerNumber;
    private final String destinationNumber;
    private final Instant startedAt;
    private final AtomicReference<CallState> state = new AtomicReference<>(CallState.CREATED);
    private final AtomicReference<UUID> currentTurnId = new AtomicReference<>();
    private volatile String bridgeId;
    private volatile String mediaChannelId;
    private volatile String mediaConnectionId;

    private CallSession(UUID internalCallId, String asteriskChannelId, String callerNumber, String destinationNumber) {
        this.internalCallId = Objects.requireNonNull(internalCallId);
        this.asteriskChannelId = Objects.requireNonNull(asteriskChannelId);
        this.callerNumber = callerNumber;
        this.destinationNumber = destinationNumber;
        this.startedAt = Instant.now();
    }

    public static CallSession create(UUID internalCallId, String asteriskChannelId, String callerNumber, String destinationNumber) {
        return new CallSession(internalCallId, asteriskChannelId, callerNumber, destinationNumber);
    }

    public boolean transitionTo(CallState target) {
        Objects.requireNonNull(target);
        for (;;) {
            CallState source = state.get();
            if (source == target && target.terminal()) return false;
            if (!TRANSITIONS.getOrDefault(source, EnumSet.noneOf(CallState.class)).contains(target)) {
                throw new IllegalStateException("Invalid call state transition: " + source + " -> " + target);
            }
            if (state.compareAndSet(source, target)) return true;
        }
    }

    public UUID startTurn() {
        UUID turnId = UUID.randomUUID();
        currentTurnId.set(turnId);
        return turnId;
    }

    public UUID interruptAndStartListening() {
        transitionTo(CallState.INTERRUPTING);
        UUID replacement = startTurn();
        transitionTo(CallState.LISTENING);
        return replacement;
    }

    public boolean isCurrentTurn(UUID turnId) { return turnId != null && turnId.equals(currentTurnId.get()); }
    public UUID internalCallId() { return internalCallId; }
    public String asteriskChannelId() { return asteriskChannelId; }
    public String callerNumber() { return callerNumber; }
    public String destinationNumber() { return destinationNumber; }
    public Instant startedAt() { return startedAt; }
    public CallState state() { return state.get(); }
    public UUID currentTurnId() { return currentTurnId.get(); }
    public String bridgeId() { return bridgeId; }
    public String mediaChannelId() { return mediaChannelId; }
    public String mediaConnectionId() { return mediaConnectionId; }
    void bindBridge(String value) { bridgeId = Objects.requireNonNull(value); }
    void bindMedia(String channelId, String connectionId) {
        mediaChannelId = Objects.requireNonNull(channelId);
        mediaConnectionId = Objects.requireNonNull(connectionId);
    }

    private static Map<CallState, EnumSet<CallState>> transitions() {
        var map = new EnumMap<CallState, EnumSet<CallState>>(CallState.class);
        map.put(CallState.CREATED, EnumSet.of(CallState.RINGING, CallState.ANSWERED, CallState.ENDING, CallState.FAILED));
        map.put(CallState.RINGING, EnumSet.of(CallState.ANSWERED, CallState.ENDING, CallState.FAILED));
        map.put(CallState.ANSWERED, EnumSet.of(CallState.LISTENING, CallState.ENDING, CallState.FAILED));
        map.put(CallState.LISTENING, EnumSet.of(CallState.THINKING, CallState.ENDING, CallState.FAILED));
        map.put(CallState.THINKING, EnumSet.of(CallState.SPEAKING, CallState.LISTENING, CallState.ENDING, CallState.FAILED));
        map.put(CallState.SPEAKING, EnumSet.of(CallState.LISTENING, CallState.INTERRUPTING, CallState.ENDING, CallState.FAILED));
        map.put(CallState.INTERRUPTING, EnumSet.of(CallState.LISTENING, CallState.ENDING, CallState.FAILED));
        map.put(CallState.ENDING, EnumSet.of(CallState.ENDED, CallState.FAILED));
        return Map.copyOf(map);
    }
}
