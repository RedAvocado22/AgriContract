#!/usr/bin/env python3
"""Validate the synchronized Markdown sources against the frozen contracts."""

from pathlib import Path
import re
import sys

import yaml


ROOT = Path(__file__).resolve().parents[3]
CONTENT = Path(__file__).resolve().parent / "content"


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def refs(doc: dict) -> set[str]:
    return {item["$ref"].split("/")[-1] for item in doc.get("oneOf", [])}


def citation_numbers(text: str) -> set[int]:
    numbers = {int(value) for value in re.findall(r"\[(\d+)\]", text)}
    for start, end in re.findall(r"\[(\d+)\]\s*[\u2013-]\s*\[(\d+)\]", text):
        numbers.update(range(int(start), int(end) + 1))
    return numbers


def validate_citations(filename: str, text: str, registry: dict[int, str]) -> None:
    marker = re.search(r"^# \*\*\d+\. Danh m\u1ee5c ngu\u1ed3n tham kh\u1ea3o\*\*$", text, flags=re.MULTILINE)
    if not marker:
        fail(f"{filename} is missing its bibliography")

    body = text[: marker.start()]
    bibliography = text[marker.end() :]
    entries = {
        int(number): " ".join(entry.split())
        for number, entry in re.findall(r"^\[(\d+)\]\s+(.+)$", bibliography, flags=re.MULTILINE)
    }
    cited = citation_numbers(body)
    if cited != set(entries):
        missing = sorted(cited - set(entries))
        unused = sorted(set(entries) - cited)
        fail(f"{filename} citation mismatch: missing entries={missing}, unused entries={unused}")

    for number, entry in entries.items():
        existing = registry.get(number)
        if existing is not None and existing != entry:
            fail(f"citation [{number}] is inconsistent across documents")
        registry[number] = entry


def markdown_table_rows(text: str, heading: str):
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if line.strip() == heading:
            for row in lines[index + 1 :]:
                if not row.startswith("|"):
                    if row.strip() and not row.startswith("|"):
                        break
                    continue
                cells = [cell.strip() for cell in row.strip().strip("|").split("|")]
                if cells[0] not in {"Method", "Event", "Command", "Schema", "---"} and not set(cells[0]) <= {"-"}:
                    yield cells
            return
    fail(f"table heading not found: {heading}")


def markdown_table_values(text: str, column: int, heading: str) -> list[str]:
    return [row[column] for row in markdown_table_rows(text, heading)]


def main() -> None:
    market = (CONTENT / "market_final.md").read_text()
    solution = (CONTENT / "solution_final.md").read_text()
    architecture = (CONTENT / "architecture_final.md").read_text()
    sds = (CONTENT / "sds_final.md").read_text()

    required_headings = {
        "market_final.md": (market, ["# **8. Current Scope**", "# **9. Known Limitations**", "# **10. Future Work**"]),
        "solution_final.md": (solution, ["# **12. Current Scope**", "# **13. Known Limitations**", "# **14. Future Work**"]),
        "architecture_final.md": (architecture, ["# **11. Current Scope**", "# **12. Known Limitations**", "# **13. Future Work**"]),
        "sds_final.md": (sds, ["# **12. Current Scope, migration và implementation plan**", "# **13. Known Limitations**", "# **14. Future Work**"]),
    }
    for filename, (text, headings) in required_headings.items():
        for heading in headings:
            if heading not in text:
                fail(f"{filename} missing required section: {heading}")

    openapi = yaml.safe_load((ROOT / "contracts/openapi/golden-flow-api.yaml").read_text())
    paths = openapi["paths"]
    operations = sum(1 for item in paths.values() for method in item if method.lower() in {"get", "post", "put", "patch", "delete"})
    if len(paths) != 41 or operations != 42:
        fail(f"OpenAPI count changed: {len(paths)} paths / {operations} operations")
    api_heading = "| Method | Path | Owner | operationId | Request, response and errors | Auth/role |"
    api_rows = list(markdown_table_rows(sds, api_heading))
    path_values = [row[1] for row in api_rows]
    expected_paths = [path for path, item in paths.items() for method in item if method.lower() in {"get", "post", "put", "patch", "delete"}]
    if path_values != expected_paths:
        fail("SDS OpenAPI path order/content does not match frozen OpenAPI")
    operations_in_order = [op for item in paths.values() for method, op in item.items() if method.lower() in {"get", "post", "put", "patch", "delete"}]
    if [row[3] for row in api_rows] != [op["operationId"] for op in operations_in_order]:
        fail("SDS operationId catalog does not match frozen OpenAPI")
    for row, op in zip(api_rows, operations_in_order):
        request = op.get("requestBody", {}).get("content", {}).get("application/json", {}).get("schema", {})
        if request.get("$ref") and request["$ref"].split("/")[-1] not in row[4]:
            fail(f"SDS request schema mismatch for {op['operationId']}")
        for status, response in op.get("responses", {}).items():
            if f"{status}:" not in row[4]:
                fail(f"SDS response status {status} missing for {op['operationId']}")
            schema = response.get("content", {}).get("application/json", {}).get("schema", {})
            if schema.get("$ref") and schema["$ref"].split("/")[-1] not in row[4]:
                fail(f"SDS response schema mismatch for {op['operationId']} status {status}")
    if len(openapi.get("components", {}).get("schemas", {})) != 38:
        fail("OpenAPI schema count is not 38")
    schema_rows = list(markdown_table_rows(sds, "| Schema | Fields/type | Constraint |"))
    if [row[0] for row in schema_rows] != list(openapi["components"]["schemas"]):
        fail("SDS schema catalog order/content does not match frozen OpenAPI")
    for row, (name, schema) in zip(schema_rows, openapi["components"]["schemas"].items()):
        for field in schema.get("properties", {}):
            if f"`{field}" not in row[1]:
                fail(f"SDS schema {name} is missing field {field}")

    events = yaml.safe_load((ROOT / "contracts/events/golden-flow-events.yaml").read_text())
    if len(refs(events)) != 44:
        fail(f"golden-flow event count is {len(refs(events))}, expected 44")
    event_heading = "| Event | Producer | Consumers | Side-effect boundary |"
    event_rows = list(markdown_table_rows(architecture, event_heading))
    event_names = [row[0] for row in event_rows]
    expected_event_names = [events["$defs"][name]["allOf"][1]["properties"]["eventType"]["const"] for name in [item["$ref"].split("/")[-1] for item in events["oneOf"]]]
    if event_names != expected_event_names:
        fail("Architecture event catalog does not match frozen event order")
    sds_event_rows = list(markdown_table_rows(sds, "| Event | Producer | Consumers | Payload fields | Nullability/idempotency/guard |"))
    if [row[0] for row in sds_event_rows] != expected_event_names:
        fail("SDS event catalog does not match frozen event order")
    for arch_row, sds_row, ref in zip(event_rows, sds_event_rows, events["oneOf"]):
        definition = events["$defs"][ref["$ref"].split("/")[-1]]["allOf"][1]
        publisher = definition["x-publisher"]
        consumers = ", ".join(definition["x-consumers"])
        if arch_row[1:3] != [publisher, consumers] or sds_row[1:3] != [publisher, consumers]:
            fail(f"producer/consumer mismatch for {arch_row[0]}")
        payload = definition["properties"]["payload"]
        for field in payload.get("properties", {}):
            if f"`{field}" not in sds_row[3]:
                fail(f"SDS event {sds_row[0]} is missing payload field {field}")

    notifications = yaml.safe_load((ROOT / "contracts/events/notification-commands.yaml").read_text())
    if len(refs(notifications)) != 16:
        fail(f"notification command count is {len(refs(notifications))}, expected 16")
    command_heading = "| Command | Producer | Consumer | Required payload; optional payload |"
    command_rows = list(markdown_table_rows(sds, command_heading))
    command_names = [row[0] for row in command_rows]
    expected_commands = [notifications["$defs"][name]["allOf"][1]["properties"]["eventType"]["const"] for name in [item["$ref"].split("/")[-1] for item in notifications["oneOf"]]]
    if command_names != expected_commands:
        fail("SDS notification command catalog does not match frozen event order")
    for row, ref in zip(command_rows, notifications["oneOf"]):
        command_definition = notifications["$defs"][ref["$ref"].split("/")[-1]]
        definition = command_definition["allOf"][1]
        if row[1] != command_definition["x-publisher"] or row[2] != ", ".join(command_definition["x-consumers"]):
            fail(f"notification producer/consumer mismatch for {row[0]}")
        payload = definition["properties"]["payload"]
        for field in payload.get("properties", {}):
            if f"`{field}`" not in row[3]:
                fail(f"notification command {row[0]} is missing payload field {field}")

    matrix_rows = re.findall(r"^\|\s*[^|]+\s*\| P[012] \|", sds, flags=re.MULTILINE)
    if len(matrix_rows) != 55:
        fail(f"verification matrix has {len(matrix_rows)} rows, expected 55")

    forbidden = [
        "source of truth", "nguồn sự thật", "single source", "Architecture/SDS",
        "_Nguồn:", "Acceptance test & source", "contract.cancelled", "initiatedBy",
        "SEIZE_PENALTY", "REFUNDED_PARTIAL", "full Zero Trust", "repository",
    ]
    combined = "\n".join([market, solution, architecture, sds])
    for phrase in forbidden:
        if phrase.lower() in combined.lower():
            fail(f"forbidden source/stale phrase remains: {phrase}")

    stale_claims = [
        "N\u011088/2019", "Ngh\u1ecb \u0111\u1ecbnh 88/2019", "5,23", "HTX nh\u1ecf kh\u00f4ng mua ph\u1ea7n m\u1ec1m",
        "ni\u1ec1m tin ho\u00e0n to\u00e0n \u0111\u1ed9c l\u1eadp", "lo\u1ea1i b\u1ecf r\u1ee7i ro vi ph\u1ea1m quy \u0111\u1ecbnh",
        "Chi\u1ebfm ~70% th\u1ecb tr\u01b0\u1eddng", "kh\u00f4ng c\u00f3 competitor tr\u1ef1c ti\u1ebfp",
    ]
    for phrase in stale_claims:
        if phrase.casefold() in combined.casefold():
            fail(f"stale or overbroad claim remains: {phrase}")
    if "5,19 t\u1ef7 USD" not in market:
        fail("market document is missing the corrected cashew export value")

    citation_registry: dict[int, str] = {}
    for filename, text in [
        ("market_final.md", market),
        ("solution_final.md", solution),
        ("architecture_final.md", architecture),
        ("sds_final.md", sds),
    ]:
        validate_citations(filename, text, citation_registry)

    print("validated: 4 Markdown sources; frozen contracts; normalized citations; stale claims removed")


if __name__ == "__main__":
    main()
