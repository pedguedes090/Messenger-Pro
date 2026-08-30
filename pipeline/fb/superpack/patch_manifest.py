#!/usr/bin/env python3
"""Clear `requiredSplitTypes="base__density"` from Facebook's AndroidManifest.xml.

A single-APK install of the merged Facebook fails with
INSTALL_FAILED_MISSING_SPLIT unless the manifest's `requiredSplitTypes`
attribute is cleared. apktool/aapt2 cannot recompile Facebook's manifest, so we
patch the binary AXML directly: empty the UTF-16LE string-pool entry for
"base__density" (length prefix -> 0, immediate null terminator).

Usage:
  python3 patch_manifest.py --apk base_desuper.apk --out base_single_unsigned.apk
"""
import argparse, os, zipfile

NEEDLE = "base__density".encode("utf-16-le")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    zin = zipfile.ZipFile(args.apk)
    mf = zin.read("AndroidManifest.xml")

    pos = mf.find(NEEDLE)
    if pos == -1:
        raise SystemExit("'base__density' not found in AndroidManifest.xml")
    if mf.find(NEEDLE, pos + 1) != -1:
        raise SystemExit("'base__density' occurs more than once; aborting")
    start = pos - 2               # 16-bit length prefix
    end = pos + len(NEEDLE) + 2   # string data + trailing null
    print("found 'base__density' at 0x{x}, region 0x{s}..0x{e}".format(
        x=pos, s=start, e=end))

    patched = bytearray(mf)
    patched[start:end] = b"\x00" * (end - start)  # empty string + null

    if os.path.exists(args.out):
        os.remove(args.out)
    zout = zipfile.ZipFile(args.out, "w", zipfile.ZIP_DEFLATED)
    for item in zin.infolist():
        if item.filename == "AndroidManifest.xml":
            zout.writestr(item.filename, bytes(patched))
        else:
            zout.writestr(item, zin.read(item.filename))
    zin.close()
    zout.close()
    print("wrote {o} ({b} bytes)".format(o=args.out, b=os.path.getsize(args.out)))


if __name__ == "__main__":
    main()
