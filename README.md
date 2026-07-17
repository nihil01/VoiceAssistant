# Voice Agent Service

Java 21 / Spring Boot WebFlux implementation of the Asterisk AI voice-agent platform. It lives alongside the Python prototype; the Python implementation is not required by this service.

## Implemented

- ARI events WebSocket with reconnect/backoff.
- Typed ARI REST gateway.
- Ordered caller answer → mixing bridge → caller add → `externalMedia` → media WS → media `StasisStart` → media add.
- `chan_websocket` server-mode media, `slin16`, `direction=both`.
- Bounded inbound/outbound audio queues, XON/XOFF handling, `FLUSH_MEDIA`, turn-aware output and barge-in cancellation.
- OpenAI Realtime STT → Responses API LLM → PCM TTS pipeline.
- PostgreSQL/R2DBC call and transcript persistence.
- Flyway migrations through JDBC at startup.
- Post-call summary/extraction worker with retry; LLM-backed when OpenAI is enabled, deterministic transcript fallback otherwise.
- Transactional post-call summary/CRM job updates.
- PostgreSQL outbox → Redis Streams relay.
- Twenty CRM background worker with idempotent job state.
- Dedicated Twenty **Звонки** projection with caller, timing, transcript, summary, topic and recording URL.
- Twenty-managed text knowledge base synchronized to local PostgreSQL/pgvector.
- Per-turn semantic knowledge retrieval with full-text fallback and graceful CRM outage handling.
- Actuator, Prometheus metrics, structured logs, non-root Docker image.

## Build and test

The host may have an old JDK. Use the Java 21 build container:

```bash
docker run --rm \
  -v "$PWD:/workspace" \
  -v "$HOME/.m2:/root/.m2" \
  -w /workspace \
  maven:3.9-eclipse-temurin-21 \
  mvn clean verify
```

## Local stack

The Compose stack contains:

- Asterisk with ARI and `chan_websocket` media;
- voice-agent service;
- voice-agent PostgreSQL and Redis;
- Twenty server and worker;
- isolated Twenty PostgreSQL and Redis.

```bash
cp .env.example .env
# Fill OpenAI, ARI and Twenty credentials only in .env or a secret manager.
docker compose up -d --build
curl -fsS http://127.0.0.1:8091/actuator/health/readiness
curl -fsS http://127.0.0.1:3000/healthz
```

Only Asterisk telephony/ARI ports, the loopback-bound voice-agent actuator port, and
the loopback-bound Twenty UI/API port are published. Both PostgreSQL and Redis pairs
remain internal. Flyway runs before the voice agent becomes ready.

The Asterisk image contains no `wget`; its Compose healthcheck intentionally uses
`asterisk -rx 'core show uptime'`.

## Twenty bootstrap

Twenty is pinned in Compose. Complete its first-run workspace setup at
`http://127.0.0.1:3000`, create a least-privilege API key in **Settings → API & Webhooks**,
and set:

```dotenv
TWENTY_ENABLED=true
TWENTY_BASE_URL=http://twenty-server:3000
TWENTY_API_KEY=...
```

Create or reconcile the custom `aiCall`, `knowledgeBaseEntry`, `callRecording`, and `voicePrompt` objects:

```bash
TWENTY_BASE_URL=http://127.0.0.1:3000 \
TWENTY_API_KEY='...' \
python3 scripts/bootstrap_twenty_schema.py
```

The bootstrap is idempotent. Validate its behavior without a live workspace:

```bash
python3 scripts/test_bootstrap_twenty_schema.py
```

The workspace exposes **Звонки** and **База знаний** as normal custom-object sections.
Create text FAQ/fact records in **База знаний** and leave `Active` enabled. The service
pulls a snapshot every 30 seconds, stores embeddings locally, and never depends on
Twenty synchronously while answering a call. Changed records are re-embedded;
disabled or deleted records are removed from runtime retrieval on the next snapshot.

The voice-agent PostgreSQL container uses `pgvector/pgvector:pg16`. Existing Docker
volumes remain compatible with the PostgreSQL 16 image; Flyway enables the `vector`
extension and creates the HNSW index.

`Note` and `Task` remain standard Twenty objects. The integration links them to a
person through `/rest/noteTargets` and `/rest/taskTargets`; it does not add fake
`personId` fields to standard objects.

## Required external configuration

For a real call:

```dotenv
ASTERISK_ENABLED=true
ASTERISK_BASE_URL=http://host.docker.internal:8088
ASTERISK_MEDIA_WS_BASE_URL=ws://host.docker.internal:8088/media
ARI_USERNAME=...
ARI_PASSWORD=...
ARI_APP=ai-agent
MEDIA_FORMAT=slin16
MEDIA_DIRECTION=both

OPENAI_ENABLED=true
OPENAI_API_KEY=...
```

For Twenty CRM, also set `TWENTY_ENABLED=true`, `TWENTY_BASE_URL`, and `TWENTY_API_KEY`.

## Production gates not replaceable by mocks

1. A real Asterisk 22.10.1/SIP call must produce binary media frames with non-zero RMS.
2. OpenAI must return a real STT final transcript.
3. TTS must be audible to the caller over `direction=both`.
4. `FLUSH_MEDIA` must stop queued playback during barge-in on the installed `chan_websocket` version.
5. Hangup must leave no bridge, media channel, WebSocket, provider session, or local call index.
6. Twenty payloads must be contract-tested against the actual workspace schema.

Do not declare production readiness from HTTP/WebSocket handshake success alone.

## Security

- No credentials have source-code defaults.
- Prefer private networking or TLS/mTLS for ARI and media.
- Treat `MEDIA_WEBSOCKET_CONNECTION_ID`, transcripts, phone numbers, and provider payloads as sensitive.
- Rotate any credentials that previously existed in the Python prototype or logs.
