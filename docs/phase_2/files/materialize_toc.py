#!/usr/bin/python3
"""Update all Writer document indexes (including TOC) and save as DOCX.

Requires LibreOffice with the Python UNO bridge. On Debian/Ubuntu, run this
script with /usr/bin/python3 so the system ``uno`` module is available.

Some valid OOXML emitted by docx.js can be opened by LibreOffice's command-line
import filter but not loaded directly through UNO. The script therefore tries a
direct update first, then performs one isolated DOCX→DOCX normalization pass
and retries. This keeps the generator deterministic while avoiding manual F9.
"""
from __future__ import annotations

import argparse
import os
import pathlib
import shutil
import socket
import subprocess
import tempfile
import time
import re
import zipfile

import uno
from com.sun.star.beans import PropertyValue


def _prop(name: str, value):
    prop = PropertyValue()
    prop.Name = name
    prop.Value = value
    return prop


def _free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def _libreoffice_binary() -> str:
    executable = shutil.which("libreoffice") or shutil.which("soffice")
    if not executable:
        raise RuntimeError("LibreOffice/soffice was not found on PATH")
    return executable


def _materialize_with_uno(input_path: pathlib.Path, output_path: pathlib.Path) -> None:
    libreoffice = _libreoffice_binary()
    input_path = input_path.resolve()
    output_path = output_path.resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    port = _free_port()
    profile_dir = pathlib.Path(tempfile.mkdtemp(prefix="agricontract-lo-profile-"))
    accept = f"socket,host=127.0.0.1,port={port};urp;StarOffice.ComponentContext"
    command = [
        libreoffice,
        "--headless",
        "--nologo",
        "--nodefault",
        "--nofirststartwizard",
        "--norestore",
        f"-env:UserInstallation={profile_dir.resolve().as_uri()}",
        f"--accept={accept}",
    ]

    process = subprocess.Popen(
        command,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        env={**os.environ, "HOME": str(profile_dir)},
    )

    document = None
    try:
        local_context = uno.getComponentContext()
        resolver = local_context.ServiceManager.createInstanceWithContext(
            "com.sun.star.bridge.UnoUrlResolver", local_context
        )

        context = None
        uno_url = f"uno:socket,host=127.0.0.1,port={port};urp;StarOffice.ComponentContext"
        for _ in range(150):
            try:
                context = resolver.resolve(uno_url)
                break
            except Exception:
                time.sleep(0.1)
        if context is None:
            raise RuntimeError("Could not connect to the LibreOffice UNO listener")

        desktop = context.ServiceManager.createInstanceWithContext(
            "com.sun.star.frame.Desktop", context
        )
        load_args = (
            _prop("Hidden", True),
            _prop("ReadOnly", False),
            _prop("UpdateDocMode", 3),
        )
        input_url = uno.systemPathToFileUrl(str(input_path))
        # A newly started headless Writer can accept the UNO connection before
        # all import filters are fully ready. Retry briefly before falling back
        # to the normalization pass.
        for _ in range(20):
            document = desktop.loadComponentFromURL(input_url, "_blank", 0, load_args)
            if document is not None:
                break
            time.sleep(0.25)
        if document is None:
            raise RuntimeError(f"LibreOffice could not load {input_path}")

        indexes = document.getDocumentIndexes()
        for index in range(indexes.getCount()):
            indexes.getByIndex(index).update()

        try:
            document.calculateAll()
        except Exception:
            pass

        document.storeAsURL(
            uno.systemPathToFileUrl(str(output_path)),
            (
                _prop("FilterName", "Office Open XML Text"),
                _prop("Overwrite", True),
            ),
        )
        document.close(True)
        document = None
    finally:
        if document is not None:
            try:
                document.close(True)
            except Exception:
                pass
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
        shutil.rmtree(profile_dir, ignore_errors=True)


def _normalize_docx(input_path: pathlib.Path) -> tuple[pathlib.Path, pathlib.Path]:
    """Return (normalized_docx, temp_root) using an isolated LO profile."""
    libreoffice = _libreoffice_binary()
    temp_root = pathlib.Path(tempfile.mkdtemp(prefix="agricontract-lo-normalize-"))
    out_dir = temp_root / "out"
    profile_dir = temp_root / "profile"
    out_dir.mkdir()
    profile_dir.mkdir()
    command = [
        libreoffice,
        "--headless",
        "--nologo",
        "--nodefault",
        "--nofirststartwizard",
        "--norestore",
        f"-env:UserInstallation={profile_dir.resolve().as_uri()}",
        "--convert-to",
        "docx",
        "--outdir",
        str(out_dir),
        str(input_path.resolve()),
    ]
    result = subprocess.run(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        env={**os.environ, "HOME": str(profile_dir)},
        timeout=120,
    )
    normalized = out_dir / input_path.name
    if result.returncode != 0 or not normalized.exists():
        shutil.rmtree(temp_root, ignore_errors=True)
        raise RuntimeError(
            "LibreOffice DOCX normalization failed: " + result.stdout.strip()
        )
    return normalized, temp_root



def _stabilize_materialized_docx(output_path: pathlib.Path) -> None:
    """Make LO-generated TOC bookmarks deterministic and keep auto-update enabled."""
    temp_path = output_path.with_suffix(output_path.suffix + ".stable.tmp")
    with zipfile.ZipFile(output_path, "r") as source, zipfile.ZipFile(
        temp_path, "w"
    ) as target:
        for info in source.infolist():
            data = source.read(info.filename)
            if info.filename == "word/document.xml":
                # LibreOffice appends a random session number to every generated
                # TOC bookmark. The prefix is unique and stable, so replace only
                # that volatile suffix while preserving all hyperlink targets.
                data = re.sub(
                    rb"(__RefHeading___Toc[0-9]+)_[0-9]+",
                    rb"\1_AgriContract",
                    data,
                )
            elif info.filename == "word/settings.xml":
                if b"<w:updateFields" in data:
                    data = re.sub(
                        rb"<w:updateFields(?:\s+w:val=\"[^\"]*\")?\s*/>",
                        b'<w:updateFields w:val="true"/>',
                        data,
                    )
                else:
                    data = data.replace(
                        b"</w:settings>",
                        b'<w:updateFields w:val="true"/></w:settings>',
                    )
            target.writestr(info, data)
    temp_path.replace(output_path)

def materialize(input_path: pathlib.Path, output_path: pathlib.Path) -> None:
    input_path = input_path.resolve()
    output_path = output_path.resolve()
    try:
        _materialize_with_uno(input_path, output_path)
        _stabilize_materialized_docx(output_path)
        return
    except Exception as direct_error:
        normalized_path = None
        temp_root = None
        try:
            normalized_path, temp_root = _normalize_docx(input_path)
            _materialize_with_uno(normalized_path, output_path)
            _stabilize_materialized_docx(output_path)
            return
        except Exception as fallback_error:
            raise RuntimeError(
                f"Direct TOC materialization failed ({direct_error}); "
                f"normalized retry also failed ({fallback_error})"
            ) from fallback_error
        finally:
            if temp_root is not None:
                shutil.rmtree(temp_root, ignore_errors=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input_docx", type=pathlib.Path)
    parser.add_argument("output_docx", type=pathlib.Path)
    args = parser.parse_args()
    materialize(args.input_docx, args.output_docx)


if __name__ == "__main__":
    main()
