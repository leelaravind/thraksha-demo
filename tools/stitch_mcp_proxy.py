#!/usr/bin/env python3
"""
Minimal stdio→HTTP MCP proxy for Google Stitch.

WHY THIS EXISTS
---------------
Stitch's MCP server advertises 15 working tools, but one of them ships a
malformed JSON Schema: `upload_design_md.outputSchema` contains

    "variantScreenInstance": { "$ref": "#/$defs/ScreenInstance" }

while that schema has **no `$defs` block at all**. Every other tool that
references `ScreenInstance` (create_project, get_project, list_projects) defines
it correctly in its own `$defs`.

Because an MCP client validates `tools/list` as a unit, that single dangling
reference makes all 15 tools fail to load:

    ! Connected · tools fetch failed — can't resolve reference #/$defs/ScreenInstance from id #

This proxy sits in front of Stitch, forwards every JSON-RPC message verbatim,
and repairs *only* that class of defect on the way back: a `$ref` that points at
`#/$defs/X` where `X` is not defined in that schema. The definition it injects is
copied from the **same tools/list response** — Stitch's own wording — so nothing
is invented here and the patch self-heals if Stitch changes the type.

It is a temporary shim. Delete it and re-point the client at Stitch directly once
the upstream schema is fixed.

SCOPE
-----
Development tooling only. Nothing here is part of the Thraksha application, is
compiled into the APK, or is reachable from app code.

USAGE
-----
    export STITCH_API_KEY=...            # never hardcoded, never logged
    python tools/stitch_mcp_proxy.py     # speaks MCP stdio on stdin/stdout

Registered with:
    claude mcp add stitch -e STITCH_API_KEY=... -- python <abs path to this file>
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

STITCH_URL = os.environ.get("STITCH_MCP_URL", "https://stitch.googleapis.com/mcp")
API_KEY_VAR = "STITCH_API_KEY"
TIMEOUT_SECONDS = 180


def log(message: str) -> None:
    """stderr only — stdout is the MCP channel and must carry nothing else."""
    print(f"[stitch-proxy] {message}", file=sys.stderr, flush=True)


# --------------------------------------------------------------------------
# the repair
# --------------------------------------------------------------------------

def _collect_refs(node, out: set[str]) -> set[str]:
    """Every '#/$defs/NAME' referenced anywhere inside `node`."""
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "$ref" and isinstance(value, str) and value.startswith("#/$defs/"):
                out.add(value.split("/")[-1])
            else:
                _collect_refs(value, out)
    elif isinstance(node, list):
        for value in node:
            _collect_refs(value, out)
    return out


def _known_definitions(tools: list) -> dict:
    """Registry of every $defs entry the server itself published, by name."""
    registry: dict = {}
    for tool in tools:
        for schema_key in ("inputSchema", "outputSchema"):
            schema = tool.get(schema_key)
            if isinstance(schema, dict):
                for name, definition in (schema.get("$defs") or {}).items():
                    registry.setdefault(name, definition)
    return registry


def _repair_schema(schema, registry: dict, where: str) -> int:
    """
    Inject definitions that are referenced but missing from THIS schema.

    Only ever adds; never edits or removes anything the server sent. Returns the
    number of definitions injected.
    """
    if not isinstance(schema, dict):
        return 0

    injected = 0
    # Iterate to a fixed point: an injected definition may itself reference
    # another missing one.
    while True:
        referenced = _collect_refs(schema, set())
        defined = set((schema.get("$defs") or {}).keys())
        missing = {
            name for name in referenced - defined if name in registry
        }
        if not missing:
            break
        schema.setdefault("$defs", {})
        for name in sorted(missing):
            schema["$defs"][name] = registry[name]
            injected += 1
            log(f"patched {where}: injected missing $defs/{name}")

    unresolvable = _collect_refs(schema, set()) - set((schema.get("$defs") or {}).keys())
    if unresolvable:
        log(f"WARNING {where}: still unresolvable -> {sorted(unresolvable)}")
    return injected


def repair_tools_list(result: dict) -> dict:
    """Repair dangling $defs references across a tools/list result."""
    tools = result.get("tools")
    if not isinstance(tools, list):
        return result

    registry = _known_definitions(tools)
    total = 0
    for tool in tools:
        for schema_key in ("inputSchema", "outputSchema"):
            if schema_key in tool:
                total += _repair_schema(
                    tool[schema_key], registry, f"{tool.get('name')}.{schema_key}"
                )
    if total:
        log(f"repaired {total} dangling reference(s) across {len(tools)} tool(s)")
    else:
        log(f"no repair needed across {len(tools)} tool(s) — upstream may be fixed")
    return result


# --------------------------------------------------------------------------
# transport
# --------------------------------------------------------------------------

def forward(message: dict, api_key: str) -> dict | None:
    """POST one JSON-RPC message to Stitch and return the decoded reply."""
    body = json.dumps(message).encode("utf-8")
    request = urllib.request.Request(
        STITCH_URL,
        data=body,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
            "X-Goog-Api-Key": api_key,
        },
    )
    with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
        raw = response.read().decode("utf-8").strip()

    if not raw:
        return None
    # Some MCP servers reply with SSE framing even for a single result.
    if raw.startswith("event:") or raw.startswith("data:"):
        for line in raw.splitlines():
            if line.startswith("data:"):
                raw = line[len("data:"):].strip()
                break
    return json.loads(raw)


def error_reply(message_id, code: int, text: str) -> dict:
    return {"jsonrpc": "2.0", "id": message_id, "error": {"code": code, "message": text}}


def main() -> int:
    api_key = os.environ.get(API_KEY_VAR)
    if not api_key:
        log(f"FATAL: {API_KEY_VAR} is not set — refusing to start")
        return 1
    log(f"proxying stdio -> {STITCH_URL}")

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            message = json.loads(line)
        except json.JSONDecodeError as exc:
            log(f"unparseable input dropped: {exc}")
            continue

        message_id = message.get("id")
        is_notification = message_id is None

        try:
            reply = forward(message, api_key)
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", "replace")[:300]
            log(f"HTTP {exc.code} for {message.get('method')}: {detail}")
            reply = None if is_notification else error_reply(
                message_id, -32603, f"Stitch HTTP {exc.code}"
            )
        except Exception as exc:  # network, timeout, decode
            log(f"forward failed for {message.get('method')}: {exc}")
            reply = None if is_notification else error_reply(
                message_id, -32603, f"proxy error: {exc}"
            )

        if reply is None:
            continue  # notifications get no response

        if message.get("method") == "tools/list" and isinstance(reply.get("result"), dict):
            reply["result"] = repair_tools_list(reply["result"])

        sys.stdout.write(json.dumps(reply) + "\n")
        sys.stdout.flush()

    return 0


if __name__ == "__main__":
    sys.exit(main())
