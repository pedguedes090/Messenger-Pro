import os

extract_dir = r'C:\Users\dun\Downloads\MessengerPro-master\MessengerPro-master\apkmess\extracted'
dex_files = sorted([os.path.join(extract_dir, f) for f in os.listdir(extract_dir) if f.endswith('.dex')])

dex_data = {}
for dex_file in dex_files:
    with open(dex_file, 'rb') as f:
        dex_data[os.path.basename(dex_file)] = f.read()

# 1. Search for ThreadThemeInfo - we know it exists as a string but path changed
# Search for class paths containing ThreadThemeInfo
print("=== 1. SEARCHING FOR ThreadThemeInfo CLASS PATH ===")
pattern = b"ThreadThemeInfo"
for dex_name, data in dex_data.items():
    pos = 0
    while True:
        idx = data.find(pattern, pos)
        if idx == -1:
            break
        # Go back to find 'L' prefix
        start = idx
        while start > 0 and data[start-1:start] != b'L' and (idx - start) < 150:
            start -= 1
        if data[start-1:start] == b'L':
            start -= 1
        # Find end ';'
        end = data.find(b";", idx)
        if end != -1 and end - start < 200:
            match = data[start:end+1]
            try:
                decoded = match.decode('utf-8', errors='replace')
                if decoded.startswith('L') and decoded.endswith(';') and '/' in decoded:
                    if all(c.isalnum() or c in 'L/;$_' for c in decoded):
                        print("  " + dex_name + ": " + decoded)
            except:
                pass
        pos = idx + 1

# 2. Search for MessagesCollection related translators
print("\n=== 2. SEARCHING FOR MESSAGE DECODER/TRANSLATOR CLASSES ===")
for search_term in [b"MessagesDecoder", b"MessageDecoder", b"MessageTranslator", 
                     b"MsysTranslator", b"CollectionTranslator",
                     b"AbstractMsys", b"msys/common"]:
    found = False
    for dex_name, data in dex_data.items():
        if search_term in data:
            found = True
    if found:
        print("  Pattern '" + search_term.decode() + "' EXISTS")
    else:
        print("  Pattern '" + search_term.decode() + "' NOT FOUND")

# Search broader - what classes decode messages?
print("\n--- Broader search: classes with 'Translator' in messaging package ---")
pattern2 = b"Lcom/facebook/messaging/"
for dex_name, data in dex_data.items():
    pos = 0
    found_set = set()
    while True:
        idx = data.find(pattern2, pos)
        if idx == -1:
            break
        end = data.find(b";", idx)
        if end != -1 and end - idx < 200:
            match = data[idx:end+1]
            try:
                decoded = match.decode('utf-8', errors='replace')
                if 'Translator' in decoded and all(c.isalnum() or c in 'L/;$_' for c in decoded):
                    found_set.add(decoded)
            except:
                pass
        pos = idx + 1
    for m in sorted(found_set):
        print("  " + m)

# 3. Search for InboxAds supplier/implementation
print("\n=== 3. SEARCHING FOR ADS SUPPLIER CLASSES ===")
for dex_name, data in dex_data.items():
    pos = 0
    found_set = set()
    while True:
        idx = data.find(b"Lcom/facebook/messaging/business/inboxads/", pos)
        if idx == -1:
            break
        end = data.find(b";", idx)
        if end != -1 and end - idx < 200:
            match = data[idx:end+1]
            try:
                decoded = match.decode('utf-8', errors='replace')
                if all(c.isalnum() or c in 'L/;$_' for c in decoded):
                    found_set.add(decoded)
            except:
                pass
        pos = idx + 1
    for m in sorted(found_set):
        print("  " + m)

# 4. Also check the dispatch methods more carefully
print("\n=== 4. VERIFYING DISPATCH METHODS ON JNI CLASSES ===")
for search in [b"dispatchV", b"dispatchO"]:
    for dex_name, data in dex_data.items():
        pos = 0
        methods = set()
        while True:
            idx = data.find(search, pos)
            if idx == -1:
                break
            # Extract method name
            end = idx
            while end < len(data) and end - idx < 50:
                b = data[end]
                if b < 0x20 or b > 0x7e:
                    break
                end += 1
            method = data[idx:end].decode('utf-8', errors='replace')
            if len(method) > 5 and method.startswith('dispatch'):
                methods.add(method)
            pos = idx + 1
        if methods:
            for m in sorted(methods):
                print("  " + dex_name + ": " + m)
