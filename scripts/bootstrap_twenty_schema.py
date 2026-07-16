#!/usr/bin/env python3
"""Idempotently provisions the custom Twenty objects used by the voice agent."""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class FieldSpec:
    name: str
    label: str
    type: str = "TEXT"


@dataclass(frozen=True)
class ObjectSpec:
    name_singular: str
    name_plural: str
    label_singular: str
    label_plural: str
    icon: str
    fields: tuple[FieldSpec, ...]


OBJECTS = (
    ObjectSpec(
        "aiCall",
        "aiCalls",
        "AI Call",
        "AI Calls",
        "IconPhoneCall",
        (
            FieldSpec("externalCallId", "External Call ID"),
            FieldSpec("callerNumber", "Caller Number"),
            FieldSpec("destinationNumber", "Destination Number"),
            FieldSpec("startedAt", "Started At", "DATE_TIME"),
            FieldSpec("endedAt", "Ended At", "DATE_TIME"),
            FieldSpec("durationSeconds", "Duration Seconds", "NUMBER"),
            FieldSpec("status", "Status"),
            FieldSpec("shortSummary", "Short Summary"),
            FieldSpec("fullSummary", "Full Summary"),
            FieldSpec("intent", "Intent"),
            FieldSpec("sentiment", "Sentiment"),
            FieldSpec("outcome", "Outcome"),
            FieldSpec("transcript", "Transcript"),
            FieldSpec("recordingUrl", "Recording URL"),
            FieldSpec("personId", "Twenty Person ID"),
        ),
    ),
    ObjectSpec(
        "callRecording",
        "callRecordings",
        "Call Recording",
        "Call Recordings",
        "IconMicrophone",
        (
            FieldSpec("externalCallId", "External Call ID"),
            FieldSpec("recordingUrl", "Recording URL"),
            FieldSpec("storageKey", "Storage Key"),
            FieldSpec("mimeType", "MIME Type"),
            FieldSpec("durationSeconds", "Duration Seconds", "NUMBER"),
            FieldSpec("status", "Status"),
            FieldSpec("aiCallId", "Twenty AI Call ID"),
        ),
    ),
    ObjectSpec(
        "voicePrompt",
        "voicePrompts",
        "Voice Prompt",
        "Voice Prompts",
        "IconMessageCode",
        (
            FieldSpec("externalPromptId", "External Prompt ID"),
            FieldSpec("assistantId", "Assistant ID"),
            FieldSpec("version", "Version", "NUMBER"),
            FieldSpec("language", "Language"),
            FieldSpec("systemPrompt", "System Prompt"),
            FieldSpec("active", "Active", "BOOLEAN"),
            FieldSpec("ttsVoice", "TTS Voice"),
            FieldSpec("ttsInstructions", "TTS Instructions"),
        ),
    ),
)


class TwentyClient:
    def __init__(self, base_url: str, api_key: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key

    def request(self, method: str, path: str, body: dict[str, Any] | None = None) -> Any:
        payload = None if body is None else json.dumps(body).encode("utf-8")
        request = urllib.request.Request(
            f"{self.base_url}{path}",
            data=payload,
            method=method,
            headers={
                "Authorization": f"Bearer {self.api_key}",
                "Accept": "application/json",
                "Content-Type": "application/json",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")[:2_000]
            raise RuntimeError(f"Twenty {method} {path} failed: HTTP {error.code}: {detail}") from error


def dictionaries(value: Any):
    if isinstance(value, dict):
        yield value
        for nested in value.values():
            yield from dictionaries(nested)
    elif isinstance(value, list):
        for nested in value:
            yield from dictionaries(nested)


def object_records(response: Any) -> dict[str, dict[str, Any]]:
    return {
        item["nameSingular"]: item
        for item in dictionaries(response)
        if isinstance(item.get("id"), str) and isinstance(item.get("nameSingular"), str)
    }


def extract_created_object(response: Any, expected_name: str) -> dict[str, Any]:
    record = object_records(response).get(expected_name)
    if record is None:
        raise RuntimeError(f"Twenty response did not contain object {expected_name}")
    return record


def field_names(response: Any, object_id: str) -> set[str]:
    return {
        item["name"]
        for item in dictionaries(response)
        if item.get("objectMetadataId") == object_id and isinstance(item.get("name"), str)
    }


def ensure_object(client: TwentyClient, spec: ObjectSpec, existing: dict[str, dict[str, Any]]) -> None:
    record = existing.get(spec.name_singular)
    if record is None:
        response = client.request(
            "POST",
            "/rest/metadata/objects",
            {
                "nameSingular": spec.name_singular,
                "namePlural": spec.name_plural,
                "labelSingular": spec.label_singular,
                "labelPlural": spec.label_plural,
                "description": "Managed by voice-agent-service",
                "icon": spec.icon,
                "isLabelSyncedWithName": False,
            },
        )
        record = extract_created_object(response, spec.name_singular)
        print(f"created object: {spec.name_singular}")
    else:
        print(f"existing object: {spec.name_singular}")

    ensure_fields(client, record["id"], spec.fields)


def ensure_fields(
    client: TwentyClient,
    object_id: str,
    fields: tuple[FieldSpec, ...],
) -> None:
    object_response = client.request("GET", f"/rest/metadata/objects/{object_id}")
    existing_fields = field_names(object_response, object_id)

    for field in fields:
        if field.name in existing_fields:
            continue
        client.request(
            "POST",
            "/rest/metadata/fields",
            {
                "objectMetadataId": object_id,
                "type": field.type,
                "name": field.name,
                "label": field.label,
                "isLabelSyncedWithName": False,
            },
        )
        print(f"  created field: {field.name}")


def main() -> int:
    base_url = os.environ.get("TWENTY_BASE_URL", "http://localhost:3000")
    api_key = os.environ.get("TWENTY_API_KEY", "").strip()
    if not api_key:
        print("TWENTY_API_KEY is required", file=sys.stderr)
        return 2

    client = TwentyClient(base_url, api_key)
    existing = object_records(client.request("GET", "/rest/metadata/objects?limit=100"))
    for spec in OBJECTS:
        ensure_object(client, spec, existing)
    print("Twenty voice schema is ready")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
