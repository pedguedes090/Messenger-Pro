#!/usr/bin/env python3
"""Extract and replace selected DEX files in a single Facebook APK."""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import re
import tempfile
import zipfile

DEX_NAMES = ("classes2.dex", "classes4.dex", "classes7.dex", "classes13.dex")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def is_signature_entry(name: str) -> bool:
    upper = name.upper()
    if not upper.startswith("META-INF/"):
        return False
    basename = upper.rsplit("/", 1)[-1]
    return (
        basename == "MANIFEST.MF"
        or basename.endswith((".SF", ".RSA", ".DSA", ".EC"))
        or basename.startswith("SIG-")
    )


def extract(apk_path: Path, output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(apk_path, "r") as apk:
        dex_entries = [
            entry
            for entry in apk.infolist()
            if re.fullmatch(r"classes(?:\d+)?\.dex", entry.filename)
        ]
        dex_names = [entry.filename for entry in dex_entries]
        if len(dex_names) != len(set(dex_names)):
            raise SystemExit("APK contains duplicate classes*.dex entries")
        missing = set(DEX_NAMES) - set(dex_names)
        if missing:
            raise SystemExit(f"required DEX missing from APK: {', '.join(sorted(missing))}")
        for entry in dex_entries:
            name = entry.filename
            data = apk.read(entry)
            if not data.startswith(b"dex\n"):
                raise SystemExit(f"invalid DEX header: {name}")
            (output_dir / name).write_bytes(data)
            print(f"extracted {name} sha256={sha256(data)}")


def replace(apk_path: Path, patch_dir: Path, output_path: Path) -> None:
    source = apk_path.resolve()
    output = output_path.resolve()
    if source == output:
        raise SystemExit("output must not overwrite the supplied APK")
    replacements = {}
    for name in DEX_NAMES:
        patch_path = patch_dir / name
        if not patch_path.is_file():
            raise SystemExit(f"patched DEX missing: {patch_path}")
        data = patch_path.read_bytes()
        if not data.startswith(b"dex\n"):
            raise SystemExit(f"invalid patched DEX header: {name}")
        replacements[name] = data

    output.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary_name = tempfile.mkstemp(prefix=output.name + ".", suffix=".tmp", dir=output.parent)
    os.close(fd)
    temporary = Path(temporary_name)
    try:
        with zipfile.ZipFile(source, "r") as original, zipfile.ZipFile(
            temporary, "w", allowZip64=True
        ) as rewritten:
            names = [entry.filename for entry in original.infolist()]
            if len(names) != len(set(names)):
                raise SystemExit("APK contains duplicate ZIP entry names; refusing an ambiguous rewrite")
            found = set()
            for entry in original.infolist():
                if is_signature_entry(entry.filename):
                    continue
                if entry.filename in replacements:
                    data = replacements[entry.filename]
                    found.add(entry.filename)
                    rewritten.writestr(entry, data)
                else:
                    rewritten.writestr(entry, original.read(entry.filename))
            missing = set(DEX_NAMES) - found
            if missing:
                raise SystemExit(f"APK DEX entries not replaced: {', '.join(sorted(missing))}")

        with zipfile.ZipFile(temporary, "r") as checked:
            for name, expected in replacements.items():
                actual = checked.read(name)
                if sha256(actual) != sha256(expected):
                    raise SystemExit(f"post-write DEX verification failed: {name}")
        os.replace(temporary, output)
    finally:
        if temporary.exists():
            temporary.unlink()

    print(f"wrote unsigned APK {output} ({output.stat().st_size} bytes)")
    for name, data in replacements.items():
        print(f"replaced {name} sha256={sha256(data)}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    extract_parser = subparsers.add_parser("extract", help="extract the DEX files patched by the 582 profile")
    extract_parser.add_argument("--apk", type=Path, required=True)
    extract_parser.add_argument("--outdir", type=Path, required=True)

    replace_parser = subparsers.add_parser("replace", help="replace patched DEX files and strip stale signatures")
    replace_parser.add_argument("--apk", type=Path, required=True)
    replace_parser.add_argument("--patch-dir", type=Path, required=True)
    replace_parser.add_argument("--out", type=Path, required=True)

    args = parser.parse_args()
    if args.command == "extract":
        extract(args.apk, args.outdir)
    else:
        replace(args.apk, args.patch_dir, args.out)


if __name__ == "__main__":
    main()
