import os

extract_dir = r'C:\Users\dun\Downloads\MessengerPro-master\MessengerPro-master\apkmess\extracted'
dex_files = sorted([os.path.join(extract_dir, f) for f in os.listdir(extract_dir) if f.endswith('.dex')])

# Load all DEX data
dex_data = {}
for dex_file in dex_files:
    with open(dex_file, 'rb') as f:
        dex_data[os.path.basename(dex_file)] = f.read()

# Search patterns for the 3 missing classes
searches = [
    # 1. MESSAGES_DECODER - AbstractMsysMessagesCollectionTranslator
    # Search for alternative translator classes
    ("AbstractMsysMessages", b"AbstractMsysMessages"),
    ("MessagesCollectionTranslator", b"MessagesCollectionTranslator"),
    ("msys/common/translator", b"msys/common/translator"),
    ("MsysMessagesCollection", b"MsysMessagesCollection"),
    
    # 2. THREAD_THEME_INFO - ThreadThemeInfo  
    ("ThreadThemeInfo", b"ThreadThemeInfo"),
    ("customthreads/model", b"customthreads/model"),
    ("ThreadTheme", b"ThreadTheme"),
    ("customthreads", b"customthreads"),
    
    # 3. ADS_SUPPLIER - InboxAdsItemSupplierImplementation
    ("InboxAdsItemSupplier", b"InboxAdsItemSupplier"),
    ("inboxads/itemsupplier", b"inboxads/itemsupplier"),
    ("InboxAds", b"InboxAds"),
    ("inboxads/plugins", b"inboxads/plugins"),
    ("AdsItemSupplier", b"AdsItemSupplier"),
    
    # Also verify important method names
    ("createWithAuthData", b"createWithAuthData"),
    ("dispatchVO", b"dispatchVO"),
    ("dispatchOOOOZ", b"dispatchOOOOZ"),
    ("/t_st", b"/t_st"),
]

print("=== SEARCHING FOR MISSING CLASSES & METHOD NAMES ===\n")

for label, pattern in searches:
    found_in = []
    for dex_name, data in dex_data.items():
        count = data.count(pattern)
        if count > 0:
            found_in.append(dex_name + "(" + str(count) + "x)")
    
    if found_in:
        files = ", ".join(found_in[:5])
        if len(found_in) > 5:
            files += " +" + str(len(found_in) - 5) + " more"
        print("[FOUND]   '" + label + "' -> " + files)
    else:
        print("[MISSING] '" + label + "'")

# Now search for full path patterns for the missing classes
print("\n=== SEARCHING ALTERNATIVE CLASS PATHS ===\n")

alt_searches = [
    # Find all classes under messaging/msys/common/translator
    (b"Lcom/facebook/messaging/msys/common/translator/", "translator classes"),
    # Find all classes under customthreads 
    (b"Lcom/facebook/messaging/customthreads/", "customthreads classes"),
    # Find InboxAds related
    (b"Lcom/facebook/messaging/business/inboxads/", "inboxads classes"),
]

for pattern, desc in alt_searches:
    print("--- " + desc + " ---")
    all_matches = set()
    for dex_name, data in dex_data.items():
        pos = 0
        while True:
            idx = data.find(pattern, pos)
            if idx == -1:
                break
            # Extract the full class path until ;
            end = data.find(b";", idx)
            if end != -1 and end - idx < 200:
                match = data[idx:end+1].decode('utf-8', errors='replace')
                # Clean up - only keep valid class paths
                if all(c.isalnum() or c in 'L/;$_' for c in match):
                    all_matches.add(match)
            pos = idx + 1
    
    if all_matches:
        for m in sorted(all_matches)[:20]:
            print("  " + m)
        if len(all_matches) > 20:
            print("  ... and " + str(len(all_matches) - 20) + " more")
    else:
        print("  (none found)")
    print()
