#!/usr/bin/env python3
"""Validate every frozen contract under contracts/.

Checks, for each *.yaml / *.yml found (new files are picked up without editing this script):
  1. it parses as YAML, and no mapping repeats a key (PyYAML silently keeps the last one);
  2. OpenAPI documents are valid OpenAPI 3.x;
  3. JSON Schema documents are valid against their meta-schema, and every $ref resolves,
     including refs into sibling files by $id (e.g. golden-flow-events -> event-envelope).

Usage: scripts/validate-contracts.py [contracts-dir]   (default: <repo>/contracts)
"""

from pathlib import Path
import sys

import yaml
from jsonschema import Draft202012Validator
from openapi_spec_validator import validate as validate_openapi
from referencing import Registry, Resource
from referencing.exceptions import Unresolvable


class UniqueKeyLoader(yaml.SafeLoader):
    """SafeLoader that rejects duplicate mapping keys instead of keeping the last value."""


def _construct_mapping(loader, node, deep=False):
    seen = set()
    for key_node, _ in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in seen:
            raise yaml.constructor.ConstructorError(
                None, None, f"duplicate key {key!r}", key_node.start_mark)
        seen.add(key)
    return yaml.SafeLoader.construct_mapping(loader, node, deep=deep)


UniqueKeyLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, _construct_mapping)


def refs(node, path="#"):
    """Yield (json-pointer-ish location, $ref value) for every $ref in the document."""
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "$ref" and isinstance(value, str):
                yield path, value
            else:
                yield from refs(value, f"{path}/{key}")
    elif isinstance(node, list):
        for index, item in enumerate(node):
            yield from refs(item, f"{path}/{index}")


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    contracts = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else root / "contracts"
    files = sorted(p for p in contracts.rglob("*") if p.suffix in {".yaml", ".yml"})
    if not files:
        print(f"ERROR: no contract files under {contracts}", file=sys.stderr)
        return 1

    errors: list[str] = []
    loaded: dict[Path, dict] = {}
    for path in files:
        name = path.relative_to(root) if path.is_relative_to(root) else path
        try:
            doc = yaml.load(path.read_text(encoding="utf-8"), Loader=UniqueKeyLoader)
        except yaml.YAMLError as e:
            errors.append(f"{name}: YAML does not parse: {e}")
            continue
        if not isinstance(doc, dict):
            errors.append(f"{name}: top level must be a mapping")
            continue
        loaded[path] = doc

    # Every JSON Schema document is registered under its $id and its file URI, so cross-file refs resolve.
    registry = Registry()
    for path, doc in loaded.items():
        if "$schema" in doc:
            resource = Resource.from_contents(doc)
            registry = registry.with_resource(path.as_uri(), resource)
            if "$id" in doc:
                registry = registry.with_resource(doc["$id"], resource)

    for path, doc in loaded.items():
        name = path.relative_to(root) if path.is_relative_to(root) else path
        if "openapi" in doc:
            try:
                validate_openapi(doc, base_uri=path.as_uri())
            except Exception as e:  # the validator raises several unrelated exception types
                errors.append(f"{name}: invalid OpenAPI: {getattr(e, 'message', e)}")
        elif "$schema" in doc:
            try:
                Draft202012Validator.check_schema(doc)
            except Exception as e:
                errors.append(f"{name}: invalid JSON Schema: {getattr(e, 'message', e)}")
                continue
            resolver = registry.resolver(base_uri=doc.get("$id", path.as_uri()))
            for location, ref in refs(doc):
                try:
                    resolver.lookup(ref)
                except Unresolvable:
                    errors.append(f"{name}: unresolved $ref {ref!r} at {location}")
        else:
            errors.append(f"{name}: neither an OpenAPI document (openapi:) nor a JSON Schema ($schema:)")

    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)
    if errors:
        return 1
    print(f"validated {len(files)} contract files: YAML parses, no duplicate keys, "
          "OpenAPI and JSON Schema valid, all $ref resolve")
    return 0


if __name__ == "__main__":
    sys.exit(main())
