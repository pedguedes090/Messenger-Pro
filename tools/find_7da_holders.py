import struct
import glob

def parse_dex(fname):
    with open(fname, 'rb') as f:
        data = f.read()
    string_ids_size = struct.unpack_from('<I', data, 56)[0]
    string_ids_off = struct.unpack_from('<I', data, 60)[0]
    type_ids_size = struct.unpack_from('<I', data, 64)[0]
    type_ids_off = struct.unpack_from('<I', data, 68)[0]
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
    
    fields = []
    for i in range(field_ids_size):
        off = field_ids_off + i * 8
        class_idx = struct.unpack_from('<H', data, off)[0]
        type_idx = struct.unpack_from('<H', data, off + 2)[0]
        name_idx = struct.unpack_from('<I', data, off + 4)[0]
        fields.append({'class': types[class_idx], 'type': types[type_idx], 'name': strings[name_idx]})
    
    return fields

for fname in sorted(glob.glob('apkmess/extracted/classes*.dex')):
    fields = parse_dex(fname)
    for f in fields:
        if f['type'] == 'LX/7Da;':
            cname = f['class'].replace('/', '.').strip('L').rstrip(';')
            print("HAS_7Da: %s.%s in %s" % (cname, f['name'], fname))
        if 'Mailbox' in f['class'] and ('7Da' in f['type'] or 'MailboxFeature' in f['type']):
            cname = f['class'].replace('/', '.').strip('L').rstrip(';')
            tname = f['type'].replace('/', '.').strip('L').rstrip(';')
            print("MAILBOX_REF: %s.%s : %s in %s" % (cname, f['name'], tname, fname))
