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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", required=True)
    ap.add_argument("--ads-dex", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    ads_bytes = open(args.ads_dex, "rb").read()
    ads_sha = hashlib.sha1(ads_bytes).hexdigest()

    zin = zipfile.ZipFile(args.apk)
    meta = zin.read("assets/secondary-program-dex-jars/metadata.txt").decode()
    new_lines = []
    for line in meta.splitlines():
        if line.startswith("secondary-1.dex "):
            parts = line.split(" ")
            new_lines.append("secondary-1.dex {sha} {canary}".format(
                sha=ads_sha, canary=parts[2]))
        else:
            new_lines.append(line)
    new_meta = "\n".join(new_lines) + "\n"

    if os.path.exists(args.out):
        os.remove(args.out)
    zout = zipfile.ZipFile(args.out, "w", zipfile.ZIP_DEFLATED)
    for item in zin.infolist():
        if item.filename == "assets/secondary-program-dex-jars/secondary-1.dex":
            zout.writestr(item.filename, ads_bytes)
        elif item.filename == "assets/secondary-program-dex-jars/metadata.txt":
            zout.writestr(item.filename, new_meta.encode())
        else:
            zout.writestr(item, zin.read(item.filename))
    zin.close()
    zout.close()
    print("wrote {o} ({b} bytes)".format(o=args.out, b=os.path.getsize(args.out)))


if __name__ == "__main__":
    main()
