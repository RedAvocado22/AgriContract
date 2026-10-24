"""Proves scripts/validate-contracts.py fails on the drift it exists to catch, and passes the real contracts."""

import importlib.util
from pathlib import Path

import pytest

SCRIPT = Path(__file__).with_name("validate-contracts.py")
spec = importlib.util.spec_from_file_location("validate_contracts", SCRIPT)
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)

OPENAPI = """openapi: 3.1.0
info: {title: t, version: "1"}
paths:
  /x:
    get:
      responses:
        "200": %s
"""

ENVELOPE = """$schema: "https://json-schema.org/draft/2020-12/schema"
$id: "https://agricontract.local/contracts/events/envelope.yaml"
type: object
properties:
  eventId: {type: string}
"""

EVENTS = """$schema: "https://json-schema.org/draft/2020-12/schema"
$id: "https://agricontract.local/contracts/events/events.yaml"
allOf:
  - $ref: "%s"
"""


def run(tmp_path, monkeypatch, files):
    for name, text in files.items():
        (tmp_path / name).parent.mkdir(parents=True, exist_ok=True)
        (tmp_path / name).write_text(text)
    monkeypatch.setattr("sys.argv", ["validate-contracts.py", str(tmp_path)])
    return validator.main()


def test_real_contracts_pass(monkeypatch):
    monkeypatch.setattr("sys.argv", ["validate-contracts.py"])
    assert validator.main() == 0


def test_valid_minimal_contracts_pass(tmp_path, monkeypatch):
    assert run(tmp_path, monkeypatch, {
        "openapi/api.yaml": OPENAPI % '{description: "ok, quoted"}',
        "events/envelope.yaml": ENVELOPE,
        "events/events.yaml": EVENTS % "./envelope.yaml",
    }) == 0


def test_unquoted_comma_in_flow_mapping_fails(tmp_path, monkeypatch, capsys):
    assert run(tmp_path, monkeypatch, {"api.yaml": OPENAPI % "{description: cut here, rest becomes a key}"}) == 1
    assert "invalid OpenAPI" in capsys.readouterr().err


def test_duplicate_key_fails(tmp_path, monkeypatch, capsys):
    assert run(tmp_path, monkeypatch, {"events/envelope.yaml": ENVELOPE + "type: string\n"}) == 1
    assert "duplicate key 'type'" in capsys.readouterr().err


def test_unparseable_yaml_fails(tmp_path, monkeypatch, capsys):
    assert run(tmp_path, monkeypatch, {"api.yaml": "openapi: [3.1.0\n"}) == 1
    assert "does not parse" in capsys.readouterr().err


def test_unresolved_cross_file_ref_fails(tmp_path, monkeypatch, capsys):
    assert run(tmp_path, monkeypatch, {
        "events/envelope.yaml": ENVELOPE,
        "events/events.yaml": EVENTS % "./missing.yaml",
    }) == 1
    assert "unresolved $ref './missing.yaml'" in capsys.readouterr().err


def test_unresolved_local_pointer_fails(tmp_path, monkeypatch, capsys):
    assert run(tmp_path, monkeypatch, {"events/events.yaml": EVENTS % "#/$defs/nope"}) == 1
    assert "unresolved $ref '#/$defs/nope'" in capsys.readouterr().err


def test_invalid_json_schema_fails(tmp_path, monkeypatch, capsys):
    assert run(tmp_path, monkeypatch, {"events/bad.yaml": ENVELOPE.replace("type: object", "type: 12")}) == 1
    assert "invalid JSON Schema" in capsys.readouterr().err


def test_unknown_document_kind_fails(tmp_path, monkeypatch, capsys):
    assert run(tmp_path, monkeypatch, {"notes.yaml": "title: not a contract\n"}) == 1
    assert "neither an OpenAPI document" in capsys.readouterr().err


@pytest.mark.parametrize("contents", ["", "- a\n- b\n"])
def test_non_mapping_top_level_fails(tmp_path, monkeypatch, contents):
    assert run(tmp_path, monkeypatch, {"x.yaml": contents}) == 1
