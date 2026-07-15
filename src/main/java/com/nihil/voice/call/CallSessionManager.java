package com.nihil.voice.call;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CallSessionManager {
    private final ConcurrentMap<UUID, CallSession> calls = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> channels = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> connections = new ConcurrentHashMap<>();

    public synchronized CallSession create(String callerChannelId, String callerNumber, String destinationNumber) {
        if (channels.containsKey(callerChannelId)) throw new IllegalStateException("Caller channel already registered: " + callerChannelId);
        var call = CallSession.create(UUID.randomUUID(), callerChannelId, callerNumber, destinationNumber);
        calls.put(call.internalCallId(), call);
        channels.put(callerChannelId, call.internalCallId());
        return call;
    }

    public synchronized void bindBridge(UUID callId, String bridgeId) { required(callId).bindBridge(bridgeId); }

    public synchronized void bindMedia(UUID callId, String mediaChannelId, String connectionId) {
        var call = required(callId);
        UUID previousChannel = channels.putIfAbsent(mediaChannelId, callId);
        UUID previousConnection = connections.putIfAbsent(connectionId, callId);
        if ((previousChannel != null && !previousChannel.equals(callId)) || (previousConnection != null && !previousConnection.equals(callId))) {
            if (previousChannel == null) channels.remove(mediaChannelId, callId);
            if (previousConnection == null) connections.remove(connectionId, callId);
            throw new IllegalStateException("Media identifier already belongs to another call");
        }
        call.bindMedia(mediaChannelId, connectionId);
    }

    public Optional<CallSession> find(UUID callId) { return Optional.ofNullable(calls.get(callId)); }
    public Optional<CallSession> findByChannel(String channelId) { return byIndex(channels, channelId); }
    public Optional<CallSession> findByConnection(String connectionId) { return byIndex(connections, connectionId); }
    public int activeCount() { return calls.size(); }

    public synchronized Optional<CallSession> remove(UUID callId) {
        CallSession removed = calls.remove(callId);
        if (removed == null) return Optional.empty();
        channels.remove(removed.asteriskChannelId(), callId);
        if (removed.mediaChannelId() != null) channels.remove(removed.mediaChannelId(), callId);
        if (removed.mediaConnectionId() != null) connections.remove(removed.mediaConnectionId(), callId);
        return Optional.of(removed);
    }

    private CallSession required(UUID id) { return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown call: " + id)); }
    private Optional<CallSession> byIndex(ConcurrentMap<String, UUID> index, String key) {
        UUID id = index.get(key);
        return id == null ? Optional.empty() : find(id);
    }
}
