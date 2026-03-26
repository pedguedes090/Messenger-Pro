import struct

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
    
    strings = []
    for i in range(string_ids_size):
        off = struct.unpack_from('<I', data, string_ids_off + i * 4)[0]
        pos = off
        size = 0
        shift = 0
        while True:
            b = data[pos]
            size |= (b & 0x7f) << shift; shift += 7; pos += 1
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

# Look for MediaSender class and its sendMediaMessage method
# Also ComposeFragment camera listener
# Also MailboxSDK class (not JNI, the wrapper) - this might have sendImageAttachment as a Java method

targets = ['MediaSender', 'ComposeFragment', 'MailboxSDK;']

for dex_num in [4, 5, 3]:
    fname = f'apkmess/extracted/classes{dex_num}.dex'
    try:
        data, strings, types, protos, methods = parse_dex_full(fname)
    except:
        continue
    
    for m in methods:
        # Look for MediaSender methods
        if 'MediaSender' in m['class'] and '$' not in m['class']:
            params = ', '.join(m['proto']['params'])
            print(f'[{fname}] {m["class"]} -> {m["name"]}({params}) : {m["proto"]["return_type"]}')
        
        # Look for MailboxSDK (not JNI) class methods  
        if m['class'] == 'Lcom/facebook/sdk/mca/MailboxSDK;':
            params = ', '.join(m['proto']['params'])
            print(f'[{fname}] MailboxSDK -> {m["name"]}({params}) : {m["proto"]["return_type"]}')
    
    # Also look for the MailboxSDK wrapper class type
    for t in types:
        if t == 'Lcom/facebook/sdk/mca/MailboxSDK;':
            print(f'[{fname}] TYPE: {t}')

# Check classes2 for MailboxSDK wrapper
for dex_num in [2]:
    fname = f'apkmess/extracted/classes{dex_num}.dex'
    try:
        data, strings, types, protos, methods = parse_dex_full(fname)
    except:
        continue
    
    for m in methods:
        if m['class'] == 'Lcom/facebook/sdk/mca/MailboxSDK;':
            params = ', '.join(m['proto']['params'])
            print(f'[{fname}] MailboxSDK -> {m["name"]}({params}) : {m["proto"]["return_type"]}')
