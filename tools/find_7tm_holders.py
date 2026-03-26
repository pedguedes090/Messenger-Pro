import struct
import glob

def parse_dex(fname):
    with open(fname, 'rb') as f:
        data = f.read()
    soff = struct.unpack_from('<I', data, 60)[0]
    ssize = struct.unpack_from('<I', data, 56)[0]
    strings = []
    for i in range(ssize):
        off = struct.unpack_from('<I', data, soff + i * 4)[0]
        pos = off; sz = 0; sh = 0
        while True:
            b = data[pos]; sz |= (b & 0x7f) << sh; sh += 7; pos += 1
            if (b & 0x80) == 0: break
        try: strings.append(data[pos:pos+sz].decode('utf-8', errors='replace'))
        except: strings.append('')
    toff = struct.unpack_from('<I', data, 68)[0]
    tsize = struct.unpack_from('<I', data, 64)[0]
    types = []
    for i in range(tsize):
        desc_idx = struct.unpack_from('<I', data, toff + i * 4)[0]
        types.append(strings[desc_idx])
    foff = struct.unpack_from('<I', data, 84)[0]
    fsize = struct.unpack_from('<I', data, 80)[0]
    fields = []
    for i in range(fsize):
        off2 = foff + i * 8
        ci = struct.unpack_from('<H', data, off2)[0]
        ti = struct.unpack_from('<H', data, off2 + 2)[0]
        ni = struct.unpack_from('<I', data, off2 + 4)[0]
        fields.append({'class': types[ci], 'type': types[ti], 'name': strings[ni]})
    return fields

# Find classes that hold a 7TM field
for fname in sorted(glob.glob('apkmess/extracted/classes*.dex')):
    fields = parse_dex(fname)
    for f in fields:
        if f['type'] == 'LX/7TM;':
            cname = f['class'].replace('/', '.').strip('L').rstrip(';')
            print("HAS_7TM: %s.%s in %s" % (cname, f['name'], fname))
        if f['type'] == 'LX/GF0;':
            cname = f['class'].replace('/', '.').strip('L').rstrip(';')
            print("HAS_GF0: %s.%s in %s" % (cname, f['name'], fname))
