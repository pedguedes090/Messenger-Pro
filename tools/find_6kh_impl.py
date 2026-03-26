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

# Find classes that implement 6kh interface
# Look for factory methods and class definitions
# Also look at 6jx, 6kd, 6kg, 6ky which have BIr() -> 6kh  (these might BE implementations)

targets = ['LX/6jx;', 'LX/6kd;', 'LX/6kg;', 'LX/6ky;', 'LX/6xY;']

for dex_num in range(1, 13):
    fname = f'apkmess/extracted/classes{dex_num}.dex' if dex_num > 1 else 'apkmess/extracted/classes.dex'
    try:
        data, strings, types, protos, methods, fields = parse_dex(fname)
    except:
        continue
    
    for target in targets:
        has = False
        for m in methods:
            if m['class'] == target:
                has = True
                break
        for f in fields:
            if f['class'] == target:
                has = True
                break
        
        if not has:
            continue
        
        cls_name = target[3:-1]
        print(f'\n=== {fname}: {cls_name} ===')
        print('Constructors:')
        for m in methods:
            if m['class'] == target and m['name'] == '<init>':
                params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
                print(f'  <init>({params})')
        
        print('Methods:')
        for m in methods:
            if m['class'] == target and m['name'] != '<init>':
                params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
                ret = m['proto']['return_type'].split('/')[-1].rstrip(';')
                print(f'  {m["name"]}({params}) -> {ret}')
        
        print('Fields:')
        for f in fields:
            if f['class'] == target:
                ftype = f['type'].split('/')[-1].rstrip(';')
                print(f'  {f["name"]} : {ftype}')

# Also look at what sendText passes as the thread key
# In MailboxConnector, sendText uses dispatchVOOOOOOO with 8 params
# The thread key is one of the Object params
# Let me look at how 7Da.A0T receives the 6kh threadKey
print("\n\n=== 7Da.A0T signature check ===")
for dex_num in range(1, 13):
    fname = f'apkmess/extracted/classes{dex_num}.dex' if dex_num > 1 else 'apkmess/extracted/classes.dex'
    try:
        data, strings, types, protos, methods, fields = parse_dex(fname)
    except:
        continue
    for m in methods:
        if m['class'] == 'LX/7Da;' and m['name'] in ('A0T', 'A0N'):
            params = ', '.join([p for p in m['proto']['params']])
            ret = m['proto']['return_type']
            print(f'  {m["name"]}({params}) -> {ret}')
