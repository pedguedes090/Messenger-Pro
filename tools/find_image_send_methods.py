import struct, sys

# Parse DEX to find class details for MailboxSDKImageAttachmentMessageOptionalParams
# and methods of MailboxSDKJNI related to sendImage

def parse_dex_strings(data):
    """Parse DEX string IDs table"""
    magic = data[:8]
    if magic[:4] != b'dex\n':
        return []
    
    string_ids_size = struct.unpack_from('<I', data, 56)[0]
    string_ids_off = struct.unpack_from('<I', data, 60)[0]
    
    strings = []
    for i in range(string_ids_size):
        off = struct.unpack_from('<I', data, string_ids_off + i * 4)[0]
        # Read ULEB128 length
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
        s = data[pos:pos+size]
        try:
            strings.append(s.decode('utf-8', errors='replace'))
        except:
            strings.append('')
    return strings

def parse_dex_types(data, strings):
    """Parse DEX type IDs"""
    type_ids_size = struct.unpack_from('<I', data, 64)[0]
    type_ids_off = struct.unpack_from('<I', data, 68)[0]
    types = []
    for i in range(type_ids_size):
        desc_idx = struct.unpack_from('<I', data, type_ids_off + i * 4)[0]
        types.append(strings[desc_idx])
    return types

def parse_dex_protos(data, strings, types):
    """Parse DEX proto IDs"""
    proto_ids_size = struct.unpack_from('<I', data, 72)[0]
    proto_ids_off = struct.unpack_from('<I', data, 76)[0]
    
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
    return protos

def parse_dex_methods(data, strings, types, protos):
    """Parse DEX method IDs"""
    method_ids_size = struct.unpack_from('<I', data, 88)[0]
    method_ids_off = struct.unpack_from('<I', data, 92)[0]
    
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
    return methods

# Search across DEX files
targets = ['MailboxSDKImageAttachment', 'sendImageAttachment', 'sendPhotoMessage', 
           'MediaSender', 'MediaMessageFactory', 'MailboxSDKJNI']

for dex_num in range(1, 13):
    fname = f'apkmess/extracted/classes{dex_num}.dex' if dex_num > 1 else 'apkmess/extracted/classes.dex'
    try:
        with open(fname, 'rb') as f:
            data = f.read()
    except FileNotFoundError:
        continue
    
    # Quick check if this DEX has our targets
    text = data.decode('ascii', errors='ignore')
    has_target = any(t in text for t in targets)
    if not has_target:
        continue
    
    print(f'\n=== {fname} ===')
    strings = parse_dex_strings(data)
    types = parse_dex_types(data, strings)
    protos = parse_dex_protos(data, strings, types)
    methods = parse_dex_methods(data, strings, types, protos)
    
    for m in methods:
        class_name = m['class']
        method_name = m['name']
        
        # Check if method or class matches our targets
        match = False
        for t in targets:
            if t in class_name or t in method_name:
                match = True
                break
        
        if match:
            params = ', '.join(m['proto']['params'])
            ret = m['proto']['return_type']
            print(f'{class_name} -> {method_name}({params}) : {ret}')
