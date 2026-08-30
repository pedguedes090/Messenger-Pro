#!/usr/bin/env python3
"""De-superpack Facebook base.apk (v576).

Facebook 576 ships its real DEX compressed in
`assets/secondary-program-dex-jars/store-0.dex.spo` (superpack). The bootstrap
`classes.dex` at the APK root reads
`assets/secondary-program-dex-jars/metadata.txt`; once the
`.superpack_files 1` / `.superpack_extension spo` pragmas are removed, the
loader falls back to reading plain `secondary-N.dex` assets directly.

This script:
  1. drops the .spo superpack blob,
  2. rewrites metadata.txt (no superpack pragmas, updated SHA-1s),
  3. injects the 18 `secondary-N.dex` assets from `--dexdir`.

The 18 decompressed dex files must already exist (produced by the superpack
decompressor -- see PIPELINE.md; today they are extracted on-device or via the
Phase-4 decompressor). Mapping: secondary-1 <- classes.dex,
secondary-N <- classesN.dex (N = 2..18).

Usage:
  python3 desuper.py --base base.apk --dexdir dex/ --out base_desuper.apk \
      [--patched5 classes5_patched.dex]
"""
import argparse, hashlib, os, zipfile


def src_for(n, dexdir, patched5):
    if n == 5 and patched5:
        return patched5
    if n == 1:
        return os.path.join(dexdir, "classes.dex")
    return os.path.join(dexdir, "classes{n}".format(n=n))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True)
    ap.add_argument("--dexdir", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--patched5", default=None,
                    help="optional seen-patched secondary-5.dex")
    args = ap.parse_args()

    dex_meta = []
    for n in range(1, 19):
        p = src_for(n, args.dexdir, args.patched5)
        if not os.path.exists(p):
            raise SystemExit("missing dex: " + p)
        b = open(p, "rb").read()
        sha = hashlib.sha1(b).hexdigest()
        dex_meta.append(("secondary-{n}.dex".format(n=n), sha,
                         "secondary.dex{n:02d}.Canary".format(n=n), p))

    metadata = "\n".join(
        "{name} {sha} {canary}".format(name=m[0], sha=m[1], canary=m[2])
        for m in dex_meta) + "\n"

    if os.path.exists(args.out):
        os.remove(args.out)
    zin = zipfile.ZipFile(args.base, "r")
    zout = zipfile.ZipFile(args.out, "w", zipfile.ZIP_DEFLATED)

    skip = {
        "assets/secondary-program-dex-jars/store-0.dex.spo",
        "assets/secondary-program-dex-jars/metadata.txt",
    }
    for item in zin.infolist():
        if item.filename in skip:
            continue
        zout.writestr(item, zin.read(item.filename))

    meta_item = zin.getinfo("assets/secondary-program-dex-jars/metadata.txt")
    zout.writestr(meta_item.filename, metadata.encode())
    for name, _sha, _canary, p in dex_meta:
        data = open(p, "rb").read()
        zout.writestr("assets/secondary-program-dex-jars/" + name, data,
                      compress_type=zipfile.ZIP_DEFLATED)

    zin.close()
    zout.close()
    print("wrote {o} ({b} bytes)".format(o=args.out, b=os.path.getsize(args.out)))


if __name__ == "__main__":
    main()
