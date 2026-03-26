import struct

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
    field_ids_size = struct.unpack_from('<I', data, 80)[0]
    field_ids_off = struct.unpack_from('<I', data, 84)[0]
    
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
    
    types = []
    for i in range(type_ids_size):
        desc_idx = struct.unpack_from('<I', data, type_ids_off + i * 4)[0]
        types.append(strings[desc_idx])
    
    protos = []
    for i in range(proto_ids_size):
        off = proto_ids_off + i * 12
        shorty_idx = struct.unpack_from('<I', data, off)[0]
        return_type_idx = struct.unpack_from('<I', data, off + 4)[0]
        params_off = struct.unpack_from('<I', data, off + 8)[0]
        param_types = []
        if params_off != 0:
            param_size = struct.unpack_from('<I', data, params_off)[0]
            for j in range(param_size):
                type_idx = struct.unpack_from('<H', data, params_off + 4 + j * 2)[0]
                param_types.append(types[type_idx])
        protos.append({'shorty': strings[shorty_idx], 'return_type': types[return_type_idx], 'params': param_types})
    
    methods = []
    for i in range(method_ids_size):
        off = method_ids_off + i * 8
        class_idx = struct.unpack_from('<H', data, off)[0]
        proto_idx = struct.unpack_from('<H', data, off + 2)[0]
        name_idx = struct.unpack_from('<I', data, off + 4)[0]
        methods.append({'class': types[class_idx], 'proto': protos[proto_idx], 'name': strings[name_idx]})
    
    fields = []
    for i in range(field_ids_size):
        off = field_ids_off + i * 8
        class_idx = struct.unpack_from('<H', data, off)[0]
        type_idx = struct.unpack_from('<H', data, off + 2)[0]
        name_idx = struct.unpack_from('<I', data, off + 4)[0]
        fields.append({'class': types[class_idx], 'type': types[type_idx], 'name': strings[name_idx]})
    
    return data, strings, types, protos, methods, fields

# Search for MailboxSDKAttachmentPreview (used by video, likely used by image too)
# And MailboxSDKImageAttachment (not MailboxSDKImageAttachmentMessageOptionalParams)
# And any class with "image" + "attachment" + "send" in msys/mca package

search_classes = ['MailboxSDKAttachmentPreview', 'MailboxSDKImageAttachment;', 
                  'ImageSource', 'MailboxSDKImageSource']

for dex_num in range(1, 13):
    fname = f'apkmess/extracted/classes{dex_num}.dex' if dex_num > 1 else 'apkmess/extracted/classes.dex'
    try:
        data, strings, types, protos, methods, fields = parse_dex(fname)
    except:
        continue
    
    # Search for the class names in types
    found_types = set()
    for t in types:
        for sc in search_classes:
            if sc in t:
                found_types.add(t)
    
    if not found_types:
        # Also check strings
        has_match = False
        for s in strings:
            for sc in search_classes:
                if sc in s:
                    has_match = True
                    break
            if has_match:
                break
        if not has_match:
            continue
    
    print(f'\n=== {fname} ===')
    
    # Print all matching types
    for t in sorted(found_types):
        print(f'TYPE: {t}')
    
    # Print all methods/fields for matching classes
    for m in methods:
        for t in found_types:
            if m['class'] == t:
                params = ', '.join(m['proto']['params'])
                print(f'  METHOD: {m["name"]}({params}) : {m["proto"]["return_type"]}')
    
    for f in fields:
        for t in found_types:
            if f['class'] == t:
                print(f'  FIELD: {f["name"]} : {f["type"]}')
    
    # Also search for 'MailboxSDKImageAttachment' as a substring in any string
    for s in strings:
        if 'MailboxSDKImageAttachment' in s and 'Optional' not in s and len(s) < 200:
            print(f'  STRING: {s}')

# Now the big question: what dispatch method does sendImageAttachment map to?
# Let's look at how sendTextMessage maps to dispatchVOOOOOOO
# Note: sendText action=71. Let's find the method named 
# "sendImageAttachmentMessageWithThreadIdentifier" on the actual class

print('\n\n=== Looking for the class that has sendImageAttachmentMessageWithThreadIdentifier as a method ===')
for dex_num in range(1, 13):
    fname = f'apkmess/extracted/classes{dex_num}.dex' if dex_num > 1 else 'apkmess/extracted/classes.dex'
    try:
        data, strings, types, protos, methods, fields = parse_dex(fname)
    except:
        continue
    
    for m in methods:
        if 'sendImageAttachment' in m['name'] or 'sendPhotoMessage' in m['name']:
            params = ', '.join(m['proto']['params'])
            print(f'[{fname}] {m["class"]} -> {m["name"]}({params}) : {m["proto"]["return_type"]}')
    
    # Also look for sendTextMessage to understand the pattern
    for m in methods:
        if m['name'] == 'sendTextMessageWithThreadIdentifier':
            params = ', '.join(m['proto']['params'])
            print(f'[{fname}] {m["class"]} -> {m["name"]}({params}) : {m["proto"]["return_type"]}')
