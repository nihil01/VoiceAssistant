#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import sys
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

MODULE_PATH = Path(__file__).with_name("bootstrap_twenty_schema.py")
SPEC = importlib.util.spec_from_file_location("twenty_bootstrap", MODULE_PATH)
bootstrap = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = bootstrap
SPEC.loader.exec_module(bootstrap)


class FakeTwentyHandler(BaseHTTPRequestHandler):
    objects = {
        "note": {"id": "note-id", "nameSingular": "note"},
        "task": {"id": "task-id", "nameSingular": "task"},
    }
    fields: dict[str, dict[str, dict]] = {"note-id": {}, "task-id": {}}
    posts = 0

    def log_message(self, *_args):
        pass

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/rest/metadata/objects":
            return self.respond({"data": {"objects": list(self.objects.values())}})
        object_id = path.rsplit("/", 1)[-1]
        record = next(item for item in self.objects.values() if item["id"] == object_id)
        return self.respond({
            "data": {
                **record,
                "fields": list(self.fields.get(object_id, {}).values()),
            }
        })

    def do_POST(self):
        type(self).posts += 1
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        if self.path == "/rest/metadata/objects":
            name = body["nameSingular"]
            record = {"id": f"{name}-id", "nameSingular": name}
            self.objects[name] = record
            self.fields[record["id"]] = {}
            return self.respond({"data": {"createObject": record}}, status=201)
        if self.path == "/rest/metadata/fields":
            object_id = body["objectMetadataId"]
            field = {
                "id": f"{object_id}-{body['name']}",
                "objectMetadataId": object_id,
                "name": body["name"],
            }
            self.fields[object_id][field["name"]] = field
            return self.respond({"data": {"createField": field}}, status=201)
        return self.respond({"error": "not found"}, status=404)

    def respond(self, value, status=200):
        encoded = json.dumps(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


class BootstrapTwentySchemaTest(unittest.TestCase):
    def test_schema_creation_is_idempotent(self):
        server = ThreadingHTTPServer(("127.0.0.1", 0), FakeTwentyHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = bootstrap.TwentyClient(
                f"http://127.0.0.1:{server.server_port}",
                "test-key",
            )
            existing = bootstrap.object_records(
                client.request("GET", "/rest/metadata/objects?limit=100")
            )
            for object_spec in bootstrap.OBJECTS:
                bootstrap.ensure_object(client, object_spec, existing)

            posts_after_first_run = FakeTwentyHandler.posts
            existing = bootstrap.object_records(
                client.request("GET", "/rest/metadata/objects?limit=100")
            )
            for object_spec in bootstrap.OBJECTS:
                bootstrap.ensure_object(client, object_spec, existing)

            self.assertEqual(posts_after_first_run, FakeTwentyHandler.posts)
            self.assertEqual(
                {
                    "note",
                    "task",
                    "aiCall",
                    "callRecording",
                    "voicePrompt",
                    "knowledgeBaseEntry",
                },
                set(FakeTwentyHandler.objects),
            )
            self.assertIn("topic", FakeTwentyHandler.fields["aiCall-id"])
            self.assertEqual(
                {
                    "title",
                    "content",
                    "category",
                    "sourceUrl",
                    "active",
                    "externalKnowledgeId",
                },
                set(FakeTwentyHandler.fields["knowledgeBaseEntry-id"]),
            )
        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    unittest.main()
