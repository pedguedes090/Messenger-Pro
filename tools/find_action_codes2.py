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
    poff = struct.unpack_from('<I', data, 76)[0]
    psize = struct.unpack_from('<I', data, 72)[0]
    protos = []
    for i in range(psize):
        off2 = poff + i * 12
        shorty_idx = struct.unpack_from('<I', data, off2)[0]
        rt = struct.unpack_from('<I', data, off2 + 4)[0]
        po = struct.unpack_from('<I', data, off2 + 8)[0]
        pts = []
        if po != 0:
            c = struct.unpack_from('<I', data, po)[0]
            for j in range(c):
                pts.append(types[struct.unpack_from('<H', data, po + 4 + j * 2)[0]])
        protos.append({'shorty': strings[shorty_idx], 'ret': types[rt], 'params': pts})
    moff = struct.unpack_from('<I', data, 92)[0]
    msize = struct.unpack_from('<I', data, 88)[0]
    methods = []
    for i in range(msize):
        off2 = moff + i * 8
        ci = struct.unpack_from('<H', data, off2)[0]
        pi = struct.unpack_from('<H', data, off2 + 2)[0]
        ni = struct.unpack_from('<I', data, off2 + 4)[0]
        methods.append({'class': types[ci], 'proto': protos[pi], 'name': strings[ni]})
    return methods, strings

# Search for the JNI method name associations
# Text: sendTextMessageWithThreadIdentifier, action=71, dispatch=dispatchVOOOOOOO
# File: sendFileAttachmentMessageWithThreadIdentifier, action=?
# Look for all JNI method name strings related to sending
for fname in sorted(glob.glob('apkmess/extracted/classes*.dex')):
    methods, strings = parse_dex(fname)
    for s in strings:
        if 'WithThreadIdentifier' in s and 'send' in s.lower():
            print("JNI_NAME: '%s' in %s" % (s, fname))

print()
# Now look at 4Eb methods - 4Eb wraps 2nZ, and Asm coroutine uses 4Eb
for fname in sorted(glob.glob('apkmess/extracted/classes*.dex')):
    methods, strings = parse_dex(fname)
    for m in methods:
        if m['class'] == 'LX/4Eb;':
            params = ', '.join([p.split('/')[-1].rstrip(';') for p in m['proto']['params']])
            ret = m['proto']['ret'].split('/')[-1].rstrip(';')
            print("4Eb.%s(%s) -> %s in %s" % (m['name'], params, ret, fname))
