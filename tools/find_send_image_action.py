import struct

# Find all callers of sendImageAttachmentMessageWithThreadIdentifier
# and understand what dispatch method+action code it maps to

# First, find the string "sendImageAttachmentMessageWithThreadIdentifier" in classes4.dex
# Then find xrefs to it, and look for dispatch method calls nearby

def parse_dex(fname):
    with open(fname, 'rb') as f:
        data = f.read()
    
    string_ids_size = struct.unpack_from('<I', data, 56)[0]
    string_ids_off = struct.unpack_from('<I', data, 60)[0]
    type_ids_size = struct.unpack_from('<I', data, 64)[0]
    type_ids_off = struct.unpack_from('<I', data, 68)[0]
    proto_ids_size = struct.unpack_from('<I', data, 72)[0]
    proto_ids_off = struct.unpack_from('<I', data, 76)[0]
    method_ids_size = struct.unpack_from('<I', data, 88)[0]
    method_ids_off = struct.unpack_from('<I', data, 92)[0]
    
    # Parse strings
    strings = []
    for i in range(string_ids_size):
        off = struct.unpack_from('<I', data, string_ids_off + i * 4)[0]
        pos = off
        size = 0
        shift = 0
        while True:
            b = data[pos]
            size |= (b & 0x7f) << shift
            shift += 7
            pos += 1
            if (b & 0x80) == 0:
                break
        try:
            strings.append(data[pos:pos+size].decode('utf-8', errors='replace'))
        except:
            strings.append('')
    
    # Parse types
    types = []
    for i in range(type_ids_size):
        desc_idx = struct.unpack_from('<I', data, type_ids_off + i * 4)[0]
        types.append(strings[desc_idx])
    
    return data, strings, types

# Look for MailboxSDKVideoAttachment too
targets_to_find = [
    'sendImageAttachmentMessageWithThreadIdentifier',
    'MailboxSDKImageAttachment', 
    'MailboxSDKVideoAttachment',
    'MailboxSDKJNI',
    'MailboxMessengerMediaSendManagerConfig',
]

for dex_num in [4, 2, 5]:
    fname = f'apkmess/extracted/classes{dex_num}.dex' if dex_num > 1 else 'apkmess/extracted/classes.dex'
    try:
        data, strings, types = parse_dex(fname)
    except:
        continue
    
    print(f'\n=== {fname} ===')
    
    # Find strings related to sendImage
    for i, s in enumerate(strings):
        for target in targets_to_find:
            if target in s and len(s) < 200:
                print(f'  string[{i}] = {s}')
    
    # Also look for action code mapping strings like "sendImageAttachment" -> dispatch pattern
    for i, s in enumerate(strings):
        if 'sendImage' in s and len(s) < 200:
            print(f'  string[{i}] = {s}')
        if 'imageAttachment' in s.lower() and len(s) < 200:
            print(f'  string[{i}] = {s}')
        if 'sendPhotoMessage' in s and len(s) < 200:
            print(f'  string[{i}] = {s}')

# Now look for what MailboxSDKJNI methods exist that could map to image sending
# The key is: sendText uses dispatchVOOOOOOO (8 params), action 71
# We need to find what action code maps to sendImageAttachment
# Look at MailboxSDKJNI's native method names registry
print('\n=== Looking for MailboxSDK native method registration ===')
for dex_num in [2, 4, 5]:
    fname = f'apkmess/extracted/classes{dex_num}.dex'
    try:
        data, strings, types = parse_dex(fname)
    except:
        continue
    
    # Find consecutive strings that look like method names with "send" 
    for i, s in enumerate(strings):
        if s.startswith('send') and 'Message' in s and len(s) < 100:
            # Look at nearby strings for context
            nearby = []
            for j in range(max(0,i-3), min(len(strings), i+4)):
                if len(strings[j]) < 120:
                    nearby.append(f'  [{j}] {strings[j]}')
            if nearby:
                print(f'\n[{fname}] Around string {i} "{s}":')
                for n in nearby:
                    print(n)
