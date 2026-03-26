import struct

# Find what classes CALL sendImageAttachmentMessageWithThreadIdentifier
# This is typically a JNI native method on some Mailbox class
# We need to find its signature (how many params, what types)
# and where it's registered

# Also search for the MailboxSDKVideoAttachment class structure

def parse_dex_full(fname):
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
    
    # Parse protos
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
        protos.append({
            'shorty': strings[shorty_idx],
            'return_type': types[return_type_idx],
            'params': param_types
        })
    
    # Parse methods
    methods = []
    for i in range(method_ids_size):
        off = method_ids_off + i * 8
        class_idx = struct.unpack_from('<H', data, off)[0]
        proto_idx = struct.unpack_from('<H', data, off + 2)[0]
        name_idx = struct.unpack_from('<I', data, off + 4)[0]
        methods.append({
            'class': types[class_idx],
            'proto': protos[proto_idx],
            'name': strings[name_idx]
        })
    
    # Parse fields
    fields = []
    for i in range(field_ids_size):
        off = field_ids_off + i * 8
        class_idx = struct.unpack_from('<H', data, off)[0]
        type_idx = struct.unpack_from('<H', data, off + 2)[0]
        name_idx = struct.unpack_from('<I', data, off + 4)[0]
        fields.append({
            'class': types[class_idx],
            'type': types[type_idx],
            'name': strings[name_idx]
        })
    
    return data, strings, types, protos, methods, fields

# Search classes2.dex and classes4.dex for relevant things
for dex_num in [2, 4, 5]:
    fname = f'apkmess/extracted/classes{dex_num}.dex'
    try:
        data, strings, types, protos, methods, fields = parse_dex_full(fname)
    except FileNotFoundError:
        continue
    
    print(f'\n=== {fname} ===')
    
    # 1. Find sendImageAttachmentMessageWithThreadIdentifier as a method
    for m in methods:
        if 'sendImageAttachment' in m['name']:
            params = ', '.join(m['proto']['params'])
            print(f'METHOD: {m["class"]} -> {m["name"]}({params}) : {m["proto"]["return_type"]}')
    
    # 2. Find MailboxSDKImageAttachmentMessageOptionalParams fields
    for f in fields:
        if 'MailboxSDKImage' in f['class'] or 'MailboxSDKVideo' in f['class']:
            print(f'FIELD: {f["class"]} . {f["name"]} : {f["type"]}')
    
    # 3. Find MailboxMessengerMediaSendManagerConfig methods
    for m in methods:
        if 'MediaSendManager' in m['class']:
            params = ', '.join(m['proto']['params'])
            print(f'METHOD: {m["class"]} -> {m["name"]}({params}) : {m["proto"]["return_type"]}')
    
    # 4. Find methods that have "ImageAttachment" params or that take a file path + threadkey 
    for m in methods:
        if 'sendPhotoMessage' in m['name'] or 'sendImageMessage' in m['name']:
            params = ', '.join(m['proto']['params'])
            print(f'SEND_METHOD: {m["class"]} -> {m["name"]}({params}) : {m["proto"]["return_type"]}')
    
    # 5. Look at MailboxSDKVideoAttachment class
    for m in methods:
        if 'MailboxSDKVideoAttachment' in m['class']:
            params = ', '.join(m['proto']['params'])
            print(f'VIDEO: {m["class"]} -> {m["name"]}({params}) : {m["proto"]["return_type"]}')
