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

# Find 7Da fields (especially how to get an instance)
# Also find what sends to A0T (text message) since we already know how to send text
# That will tell us how to get the 7Da instance

for dex_num in [4, 5]:
    fname = f'apkmess/extracted/classes{dex_num}.dex'
    try:
        data, strings, types, protos, methods, fields = parse_dex(fname)
    except:
        continue
    
    print(f'\n=== {fname}: Fields of LX/7Da; ===')
    for f in fields:
        if f['class'] == 'LX/7Da;':
            print(f'  {f["name"]} : {f["type"]}')
    
    # Find what classes REFERENCE 7Da as a field
    print(f'\n=== {fname}: Classes with field of type LX/7Da; ===')
    for f in fields:
        if f['type'] == 'LX/7Da;':
            cls = f['class'].split('/')[-1].rstrip(';')
            print(f'  {cls}.{f["name"]} : 7Da')
    
    # Find constructors of 7Da
    print(f'\n=== {fname}: 7Da constructors ===')
    for m in methods:
        if m['class'] == 'LX/7Da;' and m['name'] == '<init>':
            params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
            print(f'  <init>({params})')
    
    # Also find 6kh constructors and fields to understand thread key wrapper  
    print(f'\n=== {fname}: 6kh constructors ===')
    for m in methods:
        if m['class'] == 'LX/6kh;' and m['name'] == '<init>':
            params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
            print(f'  <init>({params})')
    
    print(f'\n=== {fname}: 6kh fields ===')
    for f in fields:
        if f['class'] == 'LX/6kh;':
            print(f'  {f["name"]} : {f["type"]}')
