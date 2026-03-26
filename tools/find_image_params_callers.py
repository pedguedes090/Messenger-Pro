import struct

# Strategy: Look at how sendTextMessage is handled in the Kotlin/Java code
# The MediaSender.sendMediaMessage calls into Messenger's internal API
# Let's look at what the A02 method on MediaMessageFactory does - it's the main entry point
# and trace what dispatch it eventually calls

# First, let's find what methods reference MailboxSDKImageAttachmentMessageOptionalParams
# in any class - this should lead us to the dispatch that sends images

# Also look for what methods reference "sendImageAttachmentMessageWithThreadIdentifier"
# as a string constant

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
        methods.append({'class': types[class_idx], 'proto': protos[proto_idx], 'name': strings[name_idx], 'idx': i})
    
    return data, strings, types, protos, methods

# For classes4.dex, find ALL methods that have MailboxSDKImageAttachmentMessageOptionalParams
# in their parameter types
for dex_num in [2, 4, 5]:
    fname = f'apkmess/extracted/classes{dex_num}.dex'
    try:
        data, strings, types, protos, methods = parse_dex(fname)
    except:
        continue
    
    target_type = 'Lcom/facebook/msys/mca/MailboxSDKImageAttachmentMessageOptionalParams;'
    
    print(f'\n=== {fname}: Methods with MailboxSDKImageAttachmentMessageOptionalParams ===')
    for m in methods:
        if target_type in m['proto']['params']:
            params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
            cls = m['class'].split('/')[-1].rstrip(';')
            print(f'  {cls}.{m["name"]}({params}) -> {m["proto"]["return_type"].split("/")[-1].rstrip(";")}')
    
    # Also check return types
    for m in methods:
        if m['proto']['return_type'] == target_type:
            params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
            cls = m['class'].split('/')[-1].rstrip(';')
            print(f'  [RETURNS] {cls}.{m["name"]}({params}) -> ImageOptParams')

    # Also find methods that reference MailboxSDKAttachmentPreview
    preview_type = 'Lcom/facebook/msys/mca/MailboxSDKAttachmentPreview;'
    print(f'\n=== {fname}: Methods with MailboxSDKAttachmentPreview (not on itself) ===')
    for m in methods:
        if preview_type in m['proto']['params'] and 'MailboxSDKAttachmentPreview' not in m['class']:
            params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
            cls = m['class'].split('/')[-1].rstrip(';')
            print(f'  {cls}.{m["name"]}({params})')
