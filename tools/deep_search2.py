import os

extract_dir = r'C:\Users\dun\Downloads\MessengerPro-master\MessengerPro-master\apkmess\extracted'
dex_files = sorted([os.path.join(extract_dir, f) for f in os.listdir(extract_dir) if f.endswith('.dex')])

dex_data = {}
for dex_file in dex_files:
    with open(dex_file, 'rb') as f:
        dex_data[os.path.basename(dex_file)] = f.read()

# 1. Look for the EXACT new path of ThreadThemeInfo
print("=== ThreadThemeInfo new path ===")
# From previous results: Lcom/facebook/xapp/messaging/threadview/theme/custom/model/ThreadThemeInfo;
new_path = b"com/facebook/xapp/messaging/threadview/theme/custom/model/ThreadThemeInfo"
for dex_name, data in dex_data.items():
    count = data.count(new_path)
    if count > 0:
        print("  " + dex_name + ": " + str(count) + " occurrences")

# Verify dot notation
new_dot = b"com.facebook.xapp.messaging.threadview.theme.custom.model.ThreadThemeInfo"
for dex_name, data in dex_data.items():
    count = data.count(new_dot)
    if count > 0:
        print("  (dot) " + dex_name + ": " + str(count) + " occurrences")

# 2. Search for possible replacement for MESSAGES_DECODER
print("\n=== Looking for class that decodes/translates MessagesCollection ===")
# The original searched for a method that contains "messageStreamingState" or "typing_indicator:"
# Let's find classes with references to "typing_indicator:" since that still exists
# We need to find what class now does message decoding

# Search for "MessagesCollection" as return type in method signatures
print("--- Classes referencing MessagesCollection ---")
mc_ref = b"MessagesCollection"
for dex_name in ["classes4.dex", "classes5.dex", "classes7.dex"]:
    data = dex_data.get(dex_name)
    if data is None:
        continue
    pos = 0
    found = set()
    while True:
        idx = data.find(mc_ref, pos)
        if idx == -1:
            break
        # Look for L prefix
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
                if decoded.startswith('L') and decoded.endswith(';') and 'Translator' in decoded:
                    found.add(decoded)
            except:
                pass
        pos = idx + 1
    for f in sorted(found):
        print("  " + dex_name + ": " + f)

# 3. Search for "xapp" messaging translator classes
print("\n--- Classes under xapp messaging with Translator/Decoder ---")
for keyword in [b"xapp/messaging", b"messaging/msys"]:
    for dex_name, data in dex_data.items():
        pos = 0
        found = set()
        while True:
            idx = data.find(keyword, pos)
            if idx == -1:
                break
            start = idx
            for i in range(1, 200):
                if idx - i >= 0 and data[idx-i] == ord('L'):
                    start = idx - i
                    break
            end = data.find(b";", idx)
            if end != -1 and end - start < 300 and start < idx:
                match = data[start:end+1]
                try:
                    decoded = match.decode('utf-8', errors='replace')
                    if decoded.startswith('L') and decoded.endswith(';'):
                        if ('Translat' in decoded or 'Decoder' in decoded or 'decoder' in decoded) and all(c.isalnum() or c in 'L/;$_' for c in decoded):
                            found.add(decoded)
                except:
                    pass
            pos = idx + 1
        for f in sorted(found):
            print("  " + dex_name + ": " + f)

# 4. Look for the method with "typing_indicator:" string reference
# That string still exists in classes5.dex - find the class
print("\n=== Looking for class containing typing_indicator: string ===")
for dex_name in ["classes5.dex", "classes.dex"]:
    data = dex_data.get(dex_name)
    if data is None:
        continue
    pattern = b"typing_indicator:"
    idx = data.find(pattern)
    if idx != -1:
        # Show surrounding context
        start = max(0, idx - 200)
        end = min(len(data), idx + 200)
        context = data[start:end]
        # Find class-like patterns nearby
        printable = ''.join(chr(b) if 32 <= b <= 126 else '.' for b in context)
        print("  Found in " + dex_name + " at offset " + str(idx))
        print("  Context: ..." + printable + "...")

# 5. Search for ads-related classes more broadly
print("\n=== ADS-related search ===")
for keyword in [b"inbox_ads", b"InboxAds", b"ads_supplier", b"AdsProvider", b"AdsLoader", b"adsLoader"]:
    found_in = []
    for dex_name, data in dex_data.items():
        count = data.count(keyword)
        if count > 0:
            found_in.append(dex_name + "(" + str(count) + "x)")
    label = keyword.decode('utf-8', errors='replace')
    if found_in:
        print("  '" + label + "' -> " + ", ".join(found_in[:5]))
    else:
        print("  '" + label + "' -> NOT FOUND")

# 6. Search for ads class under messaging/business
print("\n--- All classes under messaging/business/inboxads ---")
keyword = b"Lcom/facebook/messaging/business/inboxads"
all_ads = set()
for dex_name, data in dex_data.items():
    pos = 0
    while True:
        idx = data.find(keyword, pos)
        if idx == -1:
            break
        end = data.find(b";", idx)
        if end != -1 and end - idx < 300:
            match = data[idx:end+1]
            try:
                decoded = match.decode('utf-8', errors='replace')
                if all(c.isalnum() or c in 'L/;$_' for c in decoded):
                    all_ads.add(decoded)
            except:
                pass
        pos = idx + 1

for a in sorted(all_ads):
    print("  " + a)

# Also check under xapp for ads
print("\n--- Classes under xapp inboxads ---")
keyword = b"xapp"
for s in [b"inboxads", b"InboxAd"]:
    for dex_name, data in dex_data.items():
        pos = 0
        found = set()
        while True:
            idx = data.find(s, pos)
            if idx == -1:
                break
            start = idx
            for i in range(1, 200):
                if idx - i >= 0 and data[idx-i] == ord('L'):
                    start = idx - i
                    break
            end = data.find(b";", idx)
            if end != -1 and end - start < 300 and start < idx:
                match = data[start:end+1]
                try:
                    decoded = match.decode('utf-8', errors='replace')
                    if decoded.startswith('L') and decoded.endswith(';') and 'xapp' in decoded:
                        if all(c.isalnum() or c in 'L/;$_' for c in decoded):
                            found.add(decoded)
                except:
                    pass
            pos = idx + 1
        for f in sorted(found):
            print("  " + f)
