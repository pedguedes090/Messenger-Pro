import os

extract_dir = r'C:\Users\dun\Downloads\MessengerPro-master\MessengerPro-master\apkmess\extracted'

dex_data = {}
for f in sorted(os.listdir(extract_dir)):
    if f.endswith('.dex'):
        with open(os.path.join(extract_dir, f), 'rb') as fh:
            dex_data[f] = fh.read()

# The Messages Decoder used string refs: "messageStreamingState", "Magic words offsets", "typing_indicator:"
# "messageStreamingState" is MISSING but "Magic words offsets" exists in classes4.dex 
# "typing_indicator:" exists in classes4.dex
# Let's find what class contains "Magic words offsets"

print("=== Finding class with 'Magic words offsets' string ===")
for dex_name, data in dex_data.items():
    idx = data.find(b"Magic words offsets")
    if idx != -1:
        print("  " + dex_name + " at offset " + str(idx))

# The original MESSAGES_DECODER points to AbstractMsysMessagesCollectionTranslator
# This class was in com.facebook.messaging.msys.common.translator package
# Package is completely gone. Need to find the new location.
# 
# The method that was hooked is the one that DECODES messages and returns MessagesCollection
# Let's search for any class that:
# 1. References MessagesCollection
# 2. Has "Magic words" or "typing_indicator:" strings

print("\n=== Finding alternative to AbstractMsysMessagesCollectionTranslator ===")
# Search for 'Magic words' to find classes related to message decoding
magic = b"Magic words"
for dex_name, data in dex_data.items():
    pos = 0
    while True:
        idx = data.find(magic, pos)
        if idx == -1:
            break
        # Check nearby context for class references
        start = max(0, idx - 500)
        end = min(len(data), idx + 500)
        chunk = data[start:end]
        # Find all class refs in this chunk
        cpos = 0
        classes = set()
        while True:
            ci = chunk.find(b"Lcom/facebook/", cpos)
            if ci == -1:
                break
            ce = chunk.find(b";", ci)
            if ce != -1 and ce - ci < 200:
                match = chunk[ci:ce+1]
                try:
                    decoded = match.decode('utf-8', errors='replace')
                    if all(c.isalnum() or c in 'L/;$_' for c in decoded):
                        classes.add(decoded)
                except:
                    pass
            cpos = ci + 1
        print("  " + dex_name + " nearby classes:")
        for c in sorted(classes):
            print("    " + c)
        pos = idx + 1

# Also try to find "messageStreamingState" variants
print("\n=== Searching for alternatives to 'messageStreamingState' ===")
for variant in [b"streaming", b"Streaming", b"streamingState", b"message_streaming", b"messageStream"]:
    found_in = []
    for dex_name, data in dex_data.items():
        count = data.count(variant)
        if count > 0:
            found_in.append(dex_name + "(" + str(count) + "x)")
    label = variant.decode()
    if found_in:
        print("  '" + label + "' -> " + ", ".join(found_in[:5]))

# Try to find what replaced the translator/decoder
print("\n=== Searching broader for message translation/factory ===")
for keyword in [b"MsysMessage", b"msysMessage", b"messageFactory", b"MessageFactory", 
                b"messagesTranslat", b"MessagesTranslat", b"decodeMessages", b"DecodeMessages"]:
    found_in = []
    for dex_name, data in dex_data.items():
        count = data.count(keyword)
        if count > 0:
            found_in.append(dex_name + "(" + str(count) + "x)")
    label = keyword.decode()
    if found_in:
        print("  '" + label + "' -> " + ", ".join(found_in[:5]))
    else:
        print("  '" + label + "' -> NOT FOUND")

# Search for the inbox_ads strings to find what replaced the ads supplier
print("\n=== Searching for inbox_ads method patterns ===")
for keyword in [b"inbox_ads_fetch", b"inbox_ads_load", b"ads_fetch", b"ads_load", b"adsLoad", b"adsFetch"]:
    found_in = []
    for dex_name, data in dex_data.items():
        count = data.count(keyword)
        if count > 0:
            found_in.append(dex_name + "(" + str(count) + "x)")
    label = keyword.decode()
    if found_in:
        print("  '" + label + "' -> " + ", ".join(found_in[:5]))
    else:
        print("  '" + label + "' -> NOT FOUND")
