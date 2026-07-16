package com.nihil.voice.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class WebClientTwentyCrmGatewayIntegrationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createsNoteThenLinksItToThePersonThroughNoteTarget() {
        List<Request> requests = new CopyOnWriteArrayList<>();
        DisposableServer server = fakeTwenty(requests);
        try {
            var gateway = gateway(server);

            StepVerifier.create(gateway.createNote("person-1", "call-1", "Summary"))
                    .verifyComplete();

            assertThat(requests).hasSize(2);
            assertThat(requests.get(0).path()).isEqualTo("rest/notes");
            assertThat(requests.get(0).body()).doesNotContainKeys("personId", "aiCallId");
            assertThat(requests.get(1).path()).isEqualTo("rest/noteTargets");
            assertThat(requests.get(1).body())
                    .containsEntry("noteId", "note-1")
                    .containsEntry("targetPersonId", "person-1");
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void createsTaskThenLinksItToThePersonThroughTaskTarget() {
        List<Request> requests = new CopyOnWriteArrayList<>();
        DisposableServer server = fakeTwenty(requests);
        try {
            var gateway = gateway(server);

            StepVerifier.create(gateway.createTask("person-1", "call-1", "Call back"))
                    .verifyComplete();

            assertThat(requests).hasSize(2);
            assertThat(requests.get(0).path()).isEqualTo("rest/tasks");
            assertThat(requests.get(0).body()).doesNotContainKeys("personId", "aiCallId");
            assertThat(requests.get(1).path()).isEqualTo("rest/taskTargets");
            assertThat(requests.get(1).body())
                    .containsEntry("taskId", "task-1")
                    .containsEntry("targetPersonId", "person-1");
        } finally {
            server.disposeNow();
        }
    }

    private WebClientTwentyCrmGateway gateway(DisposableServer server) {
        var properties = new TwentyCrmProperties(
                "http://127.0.0.1:" + server.port(),
                "test-key",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        return new WebClientTwentyCrmGateway(WebClient.builder(), properties);
    }

    private DisposableServer fakeTwenty(List<Request> requests) {
        return HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes
                        .post("/rest/notes", (request, response) -> capture(
                                request,
                                response,
                                requests,
                                "{\"data\":{\"createNote\":{\"id\":\"note-1\"}}}"
                        ))
                        .post("/rest/noteTargets", (request, response) -> capture(
                                request,
                                response,
                                requests,
                                "{\"data\":{\"createNoteTarget\":{\"id\":\"note-target-1\"}}}"
                        ))
                        .post("/rest/tasks", (request, response) -> capture(
                                request,
                                response,
                                requests,
                                "{\"data\":{\"createTask\":{\"id\":\"task-1\"}}}"
                        ))
                        .post("/rest/taskTargets", (request, response) -> capture(
                                request,
                                response,
                                requests,
                                "{\"data\":{\"createTaskTarget\":{\"id\":\"task-target-1\"}}}"
                        )))
                .bindNow();
    }

    private Mono<Void> capture(
            reactor.netty.http.server.HttpServerRequest request,
            reactor.netty.http.server.HttpServerResponse response,
            List<Request> requests,
            String responseBody
    ) {
        return request.receive()
                .aggregate()
                .asString()
                .flatMap(body -> {
                    requests.add(new Request(request.path(), mapper.readValue(body, java.util.Map.class)));
                    return response
                            .header("content-type", "application/json")
                            .sendString(Mono.just(responseBody))
                            .then();
                });
    }

    private record Request(String path, java.util.Map<String, Object> body) {}
}
