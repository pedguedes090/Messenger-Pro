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
    
    return data, strings, types, protos, methods

# Find all methods on class 7Da and related classes
# 7Da.A0N is the image send method
# 6kh is probably the thread key wrapper
# Let's also find what 7Da extends/implements

for dex_num in [4, 5]:
    fname = f'apkmess/extracted/classes{dex_num}.dex'
    try:
        data, strings, types, protos, methods = parse_dex(fname)
    except:
        continue
    
    print(f'\n=== {fname}: All methods of class LX/7Da; ===')
    for m in methods:
        if m['class'] == 'LX/7Da;':
            params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
            ret = m['proto']['return_type'].split('/')[-1].rstrip(';')
            print(f'  {m["name"]}({params}) -> {ret}')
    
    # Also see what class 6kh is
    print(f'\n=== {fname}: Methods of LX/6kh; ===')
    for m in methods:
        if m['class'] == 'LX/6kh;':
            params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
            ret = m['proto']['return_type'].split('/')[-1].rstrip(';')
            print(f'  {m["name"]}({params}) -> {ret}')
    
    # Also look at 9h7 class
    print(f'\n=== {fname}: Methods of LX/9h7; ===')
    for m in methods:
        if m['class'] == 'LX/9h7;':
            params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
            ret = m['proto']['return_type'].split('/')[-1].rstrip(';')
            print(f'  {m["name"]}({params}) -> {ret}')
    
    # Look at 4Eb class
    print(f'\n=== {fname}: Methods of LX/4Eb; ===')
    count = 0
    for m in methods:
        if m['class'] == 'LX/4Eb;':
            params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
            ret = m['proto']['return_type'].split('/')[-1].rstrip(';')
            print(f'  {m["name"]}({params}) -> {ret}')
            count += 1
            if count > 20:
                print(f'  ... (truncated)')
                break
