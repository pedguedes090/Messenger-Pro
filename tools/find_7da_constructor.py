import struct
import glob

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
    
    return methods, fields

# Find how 7Da is constructed - look for its constructor
# Also find Mailbox methods that return 7Da
for fname in sorted(glob.glob('apkmess/extracted/classes*.dex')):
    methods, fields = parse_dex(fname)
    
    for m in methods:
        # 7Da constructors
        if m['class'] == 'LX/7Da;' and m['name'] == '<init>':
            params = ', '.join([p.replace('/', '.').strip('L').rstrip(';') for p in m['proto']['params']])
            print("7Da CONSTRUCTOR: <init>(%s) in %s" % (params, fname))
        
        # Methods that RETURN 7Da
        if m['proto']['return_type'] == 'LX/7Da;' and m['class'] != 'LX/7Da;':
            params = ', '.join([p.replace('/', '.').strip('L').rstrip(';') for p in m['proto']['params']])
            cname = m['class'].replace('/', '.').strip('L').rstrip(';')
            print("RETURNS_7Da: %s.%s(%s) in %s" % (cname, m['name'], params, fname))
        
        # Mailbox methods
        if 'Mailbox' in m['class'] and not '$' in m['class']:
            ret = m['proto']['return_type'].replace('/', '.').strip('L').rstrip(';')
            params = ', '.join([p.replace('/', '.').strip('L').rstrip(';') for p in m['proto']['params']])
            if '7Da' in ret or 'Feature' in ret:
                cname = m['class'].replace('/', '.').strip('L').rstrip(';')
                print("MAILBOX_METHOD: %s.%s(%s) -> %s in %s" % (cname, m['name'], params, ret, fname))
