#!/usr/bin/env python3
"""Swap the ads-patched secondary-1.dex into a merged Facebook APK.

The feed-ads patch (PatchAds.java) rewrites
LX/1lJ;.addNewEdgeToCollection in secondary-1.dex (= classes.dex). This script
replaces the secondary-1.dex asset and refreshes its SHA-1 in metadata.txt.

Usage:
  python3 integrate_ads.py --apk merged.apk --ads-dex classes_ads_patched.dex \
      --out final_unsigned.apk
"""
import argparse, hashlib, os, zipfile


def patch_dex(apk, dex_path, secondary_name, meta_lines):
    if not dex_path or not os.path.exists(dex_path):
        return meta_lines
    dex_bytes = open(dex_path, "rb").read()
    dex_sha = hashlib.sha1(dex_bytes).hexdigest()
    original_line = None
    for line in meta_lines:
        if line.startswith(f"{secondary_name} "):
            original_line = line
            break
    if original_line is None:
        print(f"WARNING: {secondary_name} not found in metadata")
        return meta_lines
    parts = original_line.split(" ")
    canary = parts[2] if len(parts) > 2 else "0" * 40
    new_line = f"{secondary_name} {dex_sha} {canary}"
    new_meta = []
    for line in meta_lines:
        if line.startswith(f"{secondary_name} "):
            new_meta.append(new_line)
        else:
            new_meta.append(line)
    return new_meta, dex_bytes, secondary_name


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", required=True)
    ap.add_argument("--ads-dex", default=None)
    ap.add_argument("--story-dex", default=None)
    ap.add_argument("--game-dex", default=None)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    zin = zipfile.ZipFile(args.apk)
    meta = zin.read("assets/secondary-program-dex-jars/metadata.txt").decode()
    meta_lines = meta.splitlines()

    patches = []
    if args.ads_dex:
        result = patch_dex(args.apk, args.ads_dex, "secondary-1.dex", meta_lines)
        if isinstance(result, tuple):
            meta_lines, dex_bytes, name = result
            patches.append((name, dex_bytes))
    if args.story_dex:
        for sec in ["secondary-5.dex", "secondary-8.dex"]:
            result = patch_dex(args.apk, args.story_dex, sec, meta_lines)
            if isinstance(result, tuple):
                meta_lines, dex_bytes, name = result
                patches.append((name, dex_bytes))
                break
    if args.game_dex:
        for sec in ["secondary-3.dex", "secondary-4.dex", "secondary-10.dex"]:
            result = patch_dex(args.apk, args.game_dex, sec, meta_lines)
            if isinstance(result, tuple):
                meta_lines, dex_bytes, name = result
                patches.append((name, dex_bytes))
                break

    if os.path.exists(args.out):
        os.remove(args.out)
    zout = zipfile.ZipFile(args.out, "w", zipfile.ZIP_DEFLATED)
    patched_files = set()
    for item in zin.infolist():
        if item.filename in [p[0] for p in patches]:
            zout.writestr(item.filename, dict(patches)[item.filename])
            patched_files.add(item.filename)
        elif item.filename == "assets/secondary-program-dex-jars/metadata.txt":
            zout.writestr(item.filename, "\n".join(meta_lines) + "\n")
        else:
            zout.writestr(item, zin.read(item.filename))
    zin.close()
    zout.close()
    print(f"wrote {args.out} ({os.path.getsize(args.out)} bytes)")
    print(f"patched dexes: {list(patched_files)}")


if __name__ == "__main__":
    main()
