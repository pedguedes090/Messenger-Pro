import struct

def parse_dex(fname):
    with open(fname, 'rb') as f:
        data = f.read()
    
    string_ids_size = struct.unpack_from('<I', data, 56)[0]
    string_ids_off = struct.unpack_from('<I', data, 60)[0]
    type_ids_size = struct.unpack_from('<I', data, 64)[0]
    type_ids_off = struct.unpack_from('<I', data, 68)[0]
    
    strings = []
    for i in range(string_ids_size):
        off = struct.unpack_from('<I', data, string_ids_off + i * 4)[0]
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
        try:
            strings.append(data[pos:pos+size].decode('utf-8', errors='replace'))
        except:
            strings.append('')
    
    types = []
    for i in range(type_ids_size):
        desc_idx = struct.unpack_from('<I', data, type_ids_off + i * 4)[0]
        types.append(strings[desc_idx])
    
    return data, strings, types

# Look for ALL classes in msys/mca package related to image/attachment
for dex_num in range(1, 13):
    fname = f'apkmess/extracted/classes{dex_num}.dex' if dex_num > 1 else 'apkmess/extracted/classes.dex'
    try:
        data, strings, types = parse_dex(fname)
    except:
        continue
    
    for t in types:
        if 'msys/mca/Mailbox' in t and t.endswith(';'):
            # Only print each class once
            classname = t[1:-1].replace('/', '.')  # Convert to Java notation
            if dex_num <= 2 or 'Image' in t or 'Video' in t or 'Media' in t or 'Attachment' in t or 'Send' in t:
                pass
            else:
                continue
            print(f'[classes{dex_num}] {classname}')
    
    # Also check for MailboxSDK... classes that might be Image related
    for t in types:
        if ('msys/mca/MailboxSDK' in t) and t.endswith(';'):
            classname = t[1:-1].replace('/', '.')
            if classname not in printed:
                printed.add(classname)

# Better approach: just find all MailboxSDK* types across all DEX
print('\n=== All MailboxSDK* types ===')
all_types = set()
for dex_num in range(1, 13):
    fname = f'apkmess/extracted/classes{dex_num}.dex' if dex_num > 1 else 'apkmess/extracted/classes.dex'
    try:
        data, strings, types = parse_dex(fname)
    except:
        continue
    
    for t in types:
        if 'MailboxSDK' in t and t.endswith(';'):
            all_types.add(t[1:-1].replace('/', '.'))

for t in sorted(all_types):
    print(f'  {t}')
