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

# Find ReadOnlyMessageMetadataDataclassAdapter and see how it creates 6kh
# Also look for simpler factories: classes that have (String) -> 6kh
# Or classes that have (long) -> 6kh

for dex_num in range(1, 13):
    fname = f'apkmess/extracted/classes{dex_num}.dex' if dex_num > 1 else 'apkmess/extracted/classes.dex'
    try:
        data, strings, types, protos, methods, fields = parse_dex(fname)
    except:
        continue
    
    for m in methods:
        # Find all methods that return 6kh
        ret = m['proto']['return_type']
        if ret == 'LX/6kh;':
            params_str = ', '.join(m['proto']['params'])
            cls = m['class'].split('/')[-1].rstrip(';')
            # Only show simple factories (few params)
            if len(m['proto']['params']) <= 2:
                print(f'{fname}: {cls}.{m["name"]}({params_str}) -> 6kh')

print("\n\n=== Methods that take String and return 6kh ===")
for dex_num in range(1, 13):
    fname = f'apkmess/extracted/classes{dex_num}.dex' if dex_num > 1 else 'apkmess/extracted/classes.dex'
    try:
        data, strings, types, protos, methods, fields = parse_dex(fname)
    except:
        continue
    for m in methods:
        if m['proto']['return_type'] == 'LX/6kh;' and len(m['proto']['params']) == 1 and 'String' in m['proto']['params'][0]:
            cls = m['class'].split('/')[-1].rstrip(';')
            print(f'{fname}: {cls}.{m["name"]}(String) -> 6kh')

print("\n\n=== Classes that have <init> and implement 6kh methods ===")
# Look for classes that have both constructor AND 6kh-like methods (AZX, AZo, etc.)
for dex_num in [4, 5]:  # Most likely DEX files
    fname = f'apkmess/extracted/classes{dex_num}.dex'
    try:
        data, strings, types, protos, methods, fields = parse_dex(fname)
    except:
        continue
    
    # Collect class methods
    class_methods = {}
    for m in methods:
        cls = m['class']
        if cls not in class_methods:
            class_methods[cls] = set()
        class_methods[cls].add(m['name'])
    
    # 6kh methods: AZX, AZo, AZq, AZr, Av4, BAl, BRF, Bbd, Bc1
    kh_methods = {'AZX', 'AZo', 'AZq', 'AZr', 'Av4', 'BAl', 'BRF', 'Bbd', 'Bc1'}
    
    for cls, mnames in class_methods.items():
        if cls == 'LX/6kh;': continue
        overlap = mnames & kh_methods
        if len(overlap) >= 3 and '<init>' in mnames:
            cls_name = cls.split('/')[-1].rstrip(';')
            # Check constructor params
            for m in methods:
                if m['class'] == cls and m['name'] == '<init>':
                    params_str = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
                    print(f'{fname}: {cls_name}.<init>({params_str}) has 6kh methods: {overlap}')
