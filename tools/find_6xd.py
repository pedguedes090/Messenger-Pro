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

# Examine 6xd class in detail
for dex_num in [4, 5]:
    fname = f'apkmess/extracted/classes{dex_num}.dex'
    try:
        data, strings, types, protos, methods, fields = parse_dex(fname)
    except:
        continue
    
    has = False
    for m in methods:
        if m['class'] == 'LX/6xd;': has = True; break
    for f in fields:
        if f['class'] == 'LX/6xd;': has = True; break
    if not has: continue
    
    print(f'\n=== {fname}: 6xd ===')
    
    print('Constructors:')
    for m in methods:
        if m['class'] == 'LX/6xd;' and m['name'] == '<init>':
            params = ', '.join(m['proto']['params'])
            print(f'  <init>({params})')
    
    print('Methods:')
    for m in methods:
        if m['class'] == 'LX/6xd;' and m['name'] != '<init>':
            params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
            ret = m['proto']['return_type'].split('/')[-1].rstrip(';')
            print(f'  {m["name"]}({params}) -> {ret}')
    
    print('Fields:')
    for f in fields:
        if f['class'] == 'LX/6xd;':
            ftype = f['type'].split('/')[-1].rstrip(';')
            print(f'  {f["name"]} : {ftype}')

# Also look at 6xY - it also has BIr(I) -> 6kh
for dex_num in [4, 5]:
    fname = f'apkmess/extracted/classes{dex_num}.dex'
    try:
        data, strings, types, protos, methods, fields = parse_dex(fname)
    except:
        continue
    
    for target in ['LX/6xY;', 'LX/85R;', 'LX/85S;', 'LX/85T;']:
        has = False
        for m in methods:
            if m['class'] == target: has = True; break
        for f in fields:
            if f['class'] == target: has = True; break
        if not has: continue
        
        cls_name = target[3:-1]
        print(f'\n=== {fname}: {cls_name} ===')
        for m in methods:
            if m['class'] == target:
                params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
                ret = m['proto']['return_type'].split('/')[-1].rstrip(';')
                print(f'  {m["name"]}({params}) -> {ret}')
        for f in fields:
            if f['class'] == target:
                ftype = f['type'].split('/')[-1].rstrip(';')
                print(f'  {f["name"]} : {ftype}')
