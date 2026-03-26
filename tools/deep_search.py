import os

extract_dir = r'C:\Users\dun\Downloads\MessengerPro-master\MessengerPro-master\apkmess\extracted'
dex_files = sorted([os.path.join(extract_dir, f) for f in os.listdir(extract_dir) if f.endswith('.dex')])

dex_data = {}
for dex_file in dex_files:
    with open(dex_file, 'rb') as f:
        dex_data[os.path.basename(dex_file)] = f.read()

# 1. Find AbstractMsys classes
print("=== SEARCHING FOR AbstractMsys* CLASSES ===")
pattern = b"AbstractMsys"
found_classes = set()
for dex_name, data in dex_data.items():
    pos = 0
    while True:
        idx = data.find(pattern, pos)
        if idx == -1:
            break
        # Go back to find 'L'
        start = idx
        for i in range(1, 150):
            if idx - i >= 0 and data[idx-i] == ord('L'):
                start = idx - i
                break
        end = data.find(b";", idx)
        if end != -1 and end - start < 300:
            match = data[start:end+1]
            try:
                decoded = match.decode('utf-8', errors='replace')
                if decoded.startswith('L') and decoded.endswith(';') and '/' in decoded:
                    if all(c.isalnum() or c in 'L/;$_' for c in decoded):
                        found_classes.add(decoded)
            except:
                pass
        pos = idx + 1

for c in sorted(found_classes):
    print("  " + c)

# 2. Find MessageTranslator classes
print("\n=== SEARCHING FOR *MessageTranslator* / *Translator* in msys ===")
for search_bytes in [b"MessageTranslator", b"Lcom/facebook/msys/", b"msys/common"]:
    label = search_bytes.decode()
    found = set()
    for dex_name, data in dex_data.items():
        pos = 0
        while True:
            idx = data.find(search_bytes, pos)
            if idx == -1:
                break
            # If it looks like a class ref, extract it
            start = idx
            for i in range(1, 150):
                if idx - i >= 0 and data[idx-i] == ord('L'):
                    start = idx - i
                    break
            end = data.find(b";", idx)
            if end != -1 and end - start < 300 and start < idx:
                match = data[start:end+1]
                try:
                    decoded = match.decode('utf-8', errors='replace')
                    if decoded.startswith('L') and decoded.endswith(';') and '/' in decoded:
                        if all(c.isalnum() or c in 'L/;$_' for c in decoded):
                            found.add(decoded)
                except:
                    pass
            pos = idx + 1
    if found:
        print("Pattern: " + label)
        for c in sorted(found):
            if 'Translator' in c or 'translator' in c or 'Decoder' in c or 'decoder' in c or 'Messages' in c:
                print("  ** " + c)

# 3. Search for strings used in unobfuscation patterns
print("\n=== VERIFYING UNOBFUSCATION STRING REFERENCES ===")
unobfuscation_strings = [
    b"messageStreamingState",
    b"Magic words offsets",
    b"typing_indicator:",
    b"COLOR_GRADIENT",
    b"ads_load_begin",
    b"inbox_ads_fetch_start",
    b"/t_st",
]
for s in unobfuscation_strings:
    found_in = []
    for dex_name, data in dex_data.items():
        count = data.count(s)
        if count > 0:
            found_in.append(dex_name + "(" + str(count) + "x)")
    label = s.decode('utf-8', errors='replace')
    if found_in:
        print("[OK]      '" + label + "' -> " + ", ".join(found_in[:5]))
    else:
        print("[MISSING] '" + label + "'")

# 4. Check for ads-related classes more broadly
print("\n=== SEARCHING FOR ADS SUPPLIER/IMPLEMENTATION CLASSES ===")
for search_bytes in [b"InboxAdsItemSupplier", b"AdsSupplier", b"adsItemSupplier", b"ads_load_begin"]:
    label = search_bytes.decode()
    found_ref = set()
    for dex_name, data in dex_data.items():
        pos = 0
        while True:
            idx = data.find(search_bytes, pos)
            if idx == -1:
                break
            start = idx
            for i in range(1, 150):
                if idx - i >= 0 and data[idx-i] == ord('L'):
                    start = idx - i
                    break
            end = data.find(b";", idx)
            if end != -1 and end - start < 300 and start < idx:
                match = data[start:end+1]
                try:
                    decoded = match.decode('utf-8', errors='replace')
                    if decoded.startswith('L') and decoded.endswith(';'):
                        if all(c.isalnum() or c in 'L/;$_' for c in decoded):
                            found_ref.add(decoded)
                except:
                    pass
            pos = idx + 1
    if found_ref:
        print("'" + label + "':")
        for c in sorted(found_ref):
            print("  " + c)
    else:
        print("'" + label + "': no class refs found")
