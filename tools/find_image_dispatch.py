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

# Find what dispatch* methods 9h7 (image send coroutine) calls
# Also find what the sendImageAttachmentMessageWithThreadIdentifier JNI method maps to

for dex_num in range(1, 13):
    fname = f'apkmess/extracted/classes{dex_num}.dex' if dex_num > 1 else 'apkmess/extracted/classes.dex'
    try:
        data, strings, types, protos, methods, fields = parse_dex(fname)
    except:
        continue
    
    # Check 9h7 class
    print(f'\n=== {fname} ===')
    for m in methods:
        if m['class'] == 'LX/9h7;':
            params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
            ret = m['proto']['return_type'].split('/')[-1].rstrip(';')
            print(f'  9h7.{m["name"]}({params}) -> {ret}')
    for f in fields:
        if f['class'] == 'LX/9h7;':
            ftype = f['type'].split('/')[-1].rstrip(';')
            print(f'  9h7.{f["name"]} : {ftype}')
    
    # Find sendImageAttachmentMessage string references
    for i, s in enumerate(strings):
        if 'sendImageAttachment' in s:
            print(f'  String[{i}]: "{s}"')
    
    # Look for MailboxSDKJNI methods that might be image-related
    # Check what methods have 'Image' or 'image' related strings
    for m in methods:
        if 'MailboxSDKJNI' in m['class'] and 'dispatch' in m['name']:
            params_desc = m['proto']['shorty']
            # Focus on dispatchVIJ methods (int, int, long + objects)
            if 'IJ' in m['name'] or 'IJ' in params_desc:
                pts = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
                print(f'  {m["class"].split("/")[-1].rstrip(";")} {m["name"]}({pts})')
