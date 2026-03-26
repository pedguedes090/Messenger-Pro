import struct, os, sys

dex_dir = 'apkmess/extracted'
path = os.path.join(dex_dir, 'classes4.dex')
with open(path, 'rb') as fp:
    data = fp.read()

target = b'sendImageAttachmentMessageWithThreadIdentifier'

# Parse DEX header
string_ids_size = struct.unpack_from('<I', data, 56)[0]
string_ids_off = struct.unpack_from('<I', data, 60)[0]
type_ids_size = struct.unpack_from('<I', data, 64)[0]
type_ids_off = struct.unpack_from('<I', data, 68)[0]
method_ids_size = struct.unpack_from('<I', data, 88)[0]
method_ids_off = struct.unpack_from('<I', data, 92)[0]

def get_string(idx):
    off = struct.unpack_from('<I', data, string_ids_off + idx * 4)[0]
    # skip ULEB128 length
    b0 = data[off]
    if b0 < 0x80:
        str_start = off + 1
    else:
        str_start = off + 2
        if data[off] >= 0xC0:
            str_start = off + 3
    end = str_start
    while end < len(data) and data[end] != 0:
        end += 1
    return data[str_start:end]

# Find string index
target_idx = -1
for i in range(string_ids_size):
    s = get_string(i)
    if s == target:
        target_idx = i
        print(f"Found target string at index {i}")
        break

if target_idx < 0:
    print("String not found")
    sys.exit(1)

# Find all methods with this name
found = False
for i in range(method_ids_size):
    off = method_ids_off + i * 8
    class_idx = struct.unpack_from('<H', data, off)[0]
    proto_idx = struct.unpack_from('<H', data, off + 2)[0]
    name_idx = struct.unpack_from('<I', data, off + 4)[0]
    if name_idx == target_idx:
        # Get class descriptor
        type_off = type_ids_off + class_idx * 4
        descriptor_idx = struct.unpack_from('<I', data, type_off)[0]
        class_name = get_string(descriptor_idx).decode('utf-8', errors='replace')
        print(f"Method {i}: class={class_name}, proto_idx={proto_idx}")
        found = True

if not found:
    print(f"No method found with name_idx={target_idx}")
    # Check a few methods around the target string index to debug
    print(f"method_ids_size={method_ids_size}")
    # Also check if the string is used as a method name by searching all methods
    # The name_idx could be > 2^31 if stored differently
    # Let's check nearby string indices
    for nearby in range(max(0, target_idx-5), min(string_ids_size, target_idx+5)):
        s = get_string(nearby).decode('utf-8', errors='replace')
        print(f"  string[{nearby}] = {s[:80]}")
