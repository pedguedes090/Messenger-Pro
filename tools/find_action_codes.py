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
        pos = off; size = 0; shift = 0
        while True:
            b = data[pos]; size |= (b & 0x7f) << shift; shift += 7; pos += 1
            if (b & 0x80) == 0: break
        try: strings.append(data[pos:pos+size].decode('utf-8', errors='replace'))
        except: strings.append('')
    
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

# The DrV class has A00(I) -> Object - that maps action code (int) to a function object
# DrV A00 is like a lookup table
# Let me find nearby strings to "sendImageAttachmentMessageWithThreadIdentifier"
# and to "sendTextMessage" to understand the action code mapping

for dex_num in [4, 5, 7, 11]:  # DEX files where MailboxSDKJNI methods are
    fname = f'apkmess/extracted/classes{dex_num}.dex' if dex_num > 1 else 'apkmess/extracted/classes.dex'
    try:
        data, strings, types, protos, methods, fields = parse_dex(fname)
    except:
        continue
    
    print(f'\n=== {fname} ===')
    
    # Find sendXxxMessage strings and nearby strings
    for i, s in enumerate(strings):
        if s.startswith('send') and ('Message' in s or 'message' in s):
            # Print context: nearby strings within +-5 indices
            print(f'  [{i}] "{s}"')
    
    # Also find dispatch method registration
    # Look for strings that look like JNI method registration tables
    for i, s in enumerate(strings):
        if 'sendImage' in s or 'sendVideo' in s or 'sendAudio' in s or 'sendFile' in s:
            print(f'  * [{i}] "{s}"')
    
    # Find all strings containing "Attachment"
    for i, s in enumerate(strings):
        if 'AttachmentMessage' in s or 'attachmentMessage' in s:
            print(f'  + [{i}] "{s}"')
