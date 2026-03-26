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
    return data, strings

# In the DEX, methods are associated with JNI names via their method_name field in the
# DrV (action lookup) class. The DrV.A00(I) -> Object method maps action codes to functions.
# The action code is a const/16 or const literal in the bytecode.
# Instead of bytecode analysis, let's look for numeric patterns near the string.

# Actually, let's look for strings that look like "sendFileAttachment*" and see nearby strings
# that might be action codes or dispatch method names
for fname in sorted(glob.glob('apkmess/extracted/classes*.dex')):
    data, strings = parse_dex(fname)
    for i, s in enumerate(strings):
        if s == 'sendFileAttachmentMessageWithThreadIdentifier':
            print("Found at string index %d in %s" % (i, fname))
            # Print nearby strings
            for j in range(max(0, i-20), min(len(strings), i+20)):
                print("  [%d] '%s'" % (j, strings[j]))
