import glob
import re
import struct

DEX_GLOB = "apkmess/extracted/classes*.dex"


def read_uleb128(data, pos):
    value = 0
    shift = 0
    while True:
        b = data[pos]
        pos += 1
        value |= (b & 0x7F) << shift
        if (b & 0x80) == 0:
            break
        shift += 7
    return value, pos


def parse_dex(path):
    with open(path, "rb") as f:
        data = f.read()

    string_ids_size = struct.unpack_from("<I", data, 56)[0]
    string_ids_off = struct.unpack_from("<I", data, 60)[0]
    type_ids_size = struct.unpack_from("<I", data, 64)[0]
    type_ids_off = struct.unpack_from("<I", data, 68)[0]
    proto_ids_size = struct.unpack_from("<I", data, 72)[0]
    proto_ids_off = struct.unpack_from("<I", data, 76)[0]
    method_ids_size = struct.unpack_from("<I", data, 88)[0]
    method_ids_off = struct.unpack_from("<I", data, 92)[0]

    strings = []
    for i in range(string_ids_size):
        off = struct.unpack_from("<I", data, string_ids_off + i * 4)[0]
        strlen, p = read_uleb128(data, off)
        raw = data[p:p + strlen]
        try:
            s = raw.decode("utf-8", errors="replace")
        except Exception:
            s = ""
        strings.append(s)

    types = []
    for i in range(type_ids_size):
        desc_idx = struct.unpack_from("<I", data, type_ids_off + i * 4)[0]
        types.append(strings[desc_idx])

    protos = []
    for i in range(proto_ids_size):
        off = proto_ids_off + i * 12
        shorty_idx = struct.unpack_from("<I", data, off)[0]
        ret_type_idx = struct.unpack_from("<I", data, off + 4)[0]
        params_off = struct.unpack_from("<I", data, off + 8)[0]

        params = []
        if params_off != 0:
            param_count = struct.unpack_from("<I", data, params_off)[0]
            for j in range(param_count):
                t_idx = struct.unpack_from("<H", data, params_off + 4 + j * 2)[0]
                params.append(types[t_idx])

        protos.append({
            "shorty": strings[shorty_idx],
            "ret": types[ret_type_idx],
            "params": params,
        })

    methods = []
    for i in range(method_ids_size):
        off = method_ids_off + i * 8
        class_idx = struct.unpack_from("<H", data, off)[0]
        proto_idx = struct.unpack_from("<H", data, off + 2)[0]
        name_idx = struct.unpack_from("<I", data, off + 4)[0]
        methods.append({
            "class": types[class_idx],
            "name": strings[name_idx],
            "proto": protos[proto_idx],
        })

    return strings, methods


def clean_type(t):
    if t.startswith("L") and t.endswith(";"):
        return t[1:-1].replace("/", ".")
    return t


def main():
    keyword = re.compile(r"(receive|received|incoming|notification|message)", re.IGNORECASE)

    for path in sorted(glob.glob(DEX_GLOB)):
        strings, methods = parse_dex(path)
        print("\n===", path, "===")

        hits = []
        for s in strings:
            if not s:
                continue
            if keyword.search(s):
                if "send" in s.lower() and "receive" not in s.lower() and "incoming" not in s.lower():
                    continue
                hits.append(s)

        uniq_hits = []
        seen = set()
        for s in hits:
            if s in seen:
                continue
            seen.add(s)
            uniq_hits.append(s)

        print("[strings]", len(uniq_hits), "hits")
        for s in uniq_hits[:80]:
            print("  -", s)

        method_hits = []
        for m in methods:
            cl = m["class"]
            nm = m["name"]
            if "Mailbox" in cl or keyword.search(nm):
                if keyword.search(nm) or ("MailboxOrcaJNI" in cl and nm.startswith("dispatch")):
                    method_hits.append(m)

        print("[methods]", len(method_hits), "hits")
        for m in method_hits[:120]:
            params = ", ".join(clean_type(p) for p in m["proto"]["params"])
            ret = clean_type(m["proto"]["ret"])
            print(f"  - {clean_type(m['class'])}.{m['name']}({params}) -> {ret}")


if __name__ == "__main__":
    main()
