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

# Check the 9h7 (image send coroutine) more carefully
# It has a field A01 : 6kh
# The question is: does 9h7 extract a String from 6kh and pass it to JNI?
# Or does 9h7 pass the 6kh directly?

# Let me look at the text send coroutine 8t6 to understand the pattern
# 8t6 has: A05:7Da, A06:String, A07:String (threadKey and text as Strings)
# So for text, the threadKey is already a String by the time it reaches the coroutine

# For image, 9h7 has: A01:6kh, A0B:String, A0C:String, A0D:String
# So the 6kh is kept as an object, not converted to String yet

# This means the conversion from 6kh to String happens inside onCompletion
# Or 6kh has a method that gives the encoded string

# Let me check 6kh interface methods again
# 6kh: AZX, AZo, AZq, AZr, Av4, BAl, BRF, Bbd, Bc1
# These return types like: 9KO, 7dl, 7ZG, 7RD, 7dq, BcC, 7ZD, 7eA, 7eB

# These return objects... Let me look at 7dl, 7ZD - these might be String wrappers

# Actually, let's look at this from a different angle:
# The ReadOnlyMessageMetadataDataclassAdapter.toAdaptedObject(String) -> 6kh
# This converts a String to 6kh
# What String format does it expect?

# Let me check the 6kh interface method return types
for dex_num in [4, 5]:
    fname = f'apkmess/extracted/classes{dex_num}.dex'
    try:
        data, strings, types, protos, methods = parse_dex(fname)
    except:
        continue
    
    for m in methods:
        if m['class'] == 'LX/6kh;':
            params = ', '.join(m['proto']['params'])
            ret = m['proto']['return_type']
            print(f'6kh: {m["name"]}({params}) -> {ret}')

# Now check what 7dl is
print("\n=== 7dl class ===")
for dex_num in [4, 5]:
    fname = f'apkmess/extracted/classes{dex_num}.dex'
    try:
        data, strings, types, protos, methods = parse_dex(fname)
    except:
        continue
    for m in methods:
        if m['class'] == 'LX/7dl;':
            params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
            ret = m['proto']['return_type'].split('/')[-1].rstrip(';')
            print(f'{fname}: 7dl.{m["name"]}({params}) -> {ret}')

# Also try a totally different approach: 
# Check what 7Da.A0Q (sendFile) expects for its Strings
# A0Q(FileAttachmentOptParams, LoggingOption, Number, String, String, String, String, String)
# These 5 Strings could be:
# - threadKey encoded
# - filePath
# - fileName
# - mimeType  
# - caption

# Check if 7Da.A0Q also has a coroutine class (like 9h7 for A0N)
# Look for classes with FileAttachmentMessageOptionalParams field + 7Da field
print("\n=== File send coroutine class ===")
for dex_num in range(1, 13):
    fname = f'apkmess/extracted/classes{dex_num}.dex' if dex_num > 1 else 'apkmess/extracted/classes.dex'
    try:
        data, strings, types, protos, methods = parse_dex(fname)
    except:
        continue
    
    # Collect field types per class
    field_data = {}
    with open(fname, 'rb') as f:
        raw = f.read()
    fid_s = struct.unpack_from('<I', raw, 80)[0]
    fid_o = struct.unpack_from('<I', raw, 84)[0]
    for i in range(fid_s):
        off = fid_o + i * 8
        class_idx = struct.unpack_from('<H', raw, off)[0]
        type_idx = struct.unpack_from('<H', raw, off + 2)[0]
        name_idx = struct.unpack_from('<I', raw, off + 4)[0]
        cls = types[class_idx]
        ftype = types[type_idx]
        if cls not in field_data:
            field_data[cls] = []
        field_data[cls].append((strings[name_idx], ftype))
    
    for cls, flds in field_data.items():
        has_7da = any('7Da' in ft for _, ft in flds)
        has_file_opt = any('FileAttachment' in ft for _, ft in flds)
        if has_7da and has_file_opt:
            cls_short = cls.split('/')[-1].rstrip(';')
            print(f'\n{fname}: {cls_short}')
            for fn, ft in flds:
                ft_short = ft.split('/')[-1].rstrip(';')
                print(f'  {fn} : {ft_short}')
