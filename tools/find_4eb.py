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

# Check class 4Eb and 2nZ - these are key intermediary classes
# 4Eb wraps 2nZ which might be the actual JNI bridge class
# Also check what methods 4Eb has

for dex_num in range(1, 13):
    fname = f'apkmess/extracted/classes{dex_num}.dex' if dex_num > 1 else 'apkmess/extracted/classes.dex'
    try:
        data, strings, types, protos, methods, fields = parse_dex(fname)
    except:
        continue
    
    for target, label in [('LX/4Eb;', '4Eb'), ('LX/2nZ;', '2nZ'), ('LX/DrV;', 'DrV')]:
        has = False
        for m in methods:
            if m['class'] == target: has = True; break
        for f in fields:
            if f['class'] == target: has = True; break
        if not has: continue
        
        print(f'\n=== {fname}: {label} ===')
        for m in methods:
            if m['class'] == target:
                params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
                ret = m['proto']['return_type'].split('/')[-1].rstrip(';')
                print(f'  M: {m["name"]}({params}) -> {ret}')
        for f in fields:
            if f['class'] == target:
                ftype = f['type'].split('/')[-1].rstrip(';')
                print(f'  F: {f["name"]} : {ftype}')

# Also look at what the sendText actually calls: 
# sendText in 7Da is "A0T" - it internally creates a MailboxFutureImpl and dispatches to the JNI
# Let me check the text send coroutine class too
# Look for a class that has: 7Da field + threadKey field + text-related fields
print("\n\n=== Looking for text send coroutine ===")
for dex_num in range(1, 13):
    fname = f'apkmess/extracted/classes{dex_num}.dex' if dex_num > 1 else 'apkmess/extracted/classes.dex'
    try:
        data, strings, types, protos, methods, fields = parse_dex(fname)
    except:
        continue
    
    # Find classes with both 7Da field and SendTextMessageOptionalParams field
    class_fields = {}
    for f in fields:
        if f['class'] not in class_fields:
            class_fields[f['class']] = []
        class_fields[f['class']].append(f)
    
    for cls, flds in class_fields.items():
        has_7da = any('7Da' in f['type'] for f in flds)
        has_text_opt = any('SendTextMessage' in f['type'] for f in flds)
        if has_7da and has_text_opt:
            print(f'\n  {fname}: {cls}')
            for f in flds:
                ftype = f['type'].split('/')[-1].rstrip(';')
                print(f'    {f["name"]} : {ftype}')
            for m in methods:
                if m['class'] == cls:
                    params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
                    ret = m['proto']['return_type'].split('/')[-1].rstrip(';')
                    print(f'    M: {m["name"]}({params}) -> {ret}')
