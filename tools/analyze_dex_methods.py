import os
import struct
import glob

def parse_dex(dex_path):
    with open(dex_path, 'rb') as f:
        data = f.read()
    
    if len(data) < 112 or data[:4] != b'dex\n':
        return []

    # Read header
    string_ids_size, string_ids_off = struct.unpack_from('<II', data, 56)
    type_ids_size, type_ids_off = struct.unpack_from('<II', data, 64)
    proto_ids_size, proto_ids_off = struct.unpack_from('<II', data, 72)
    method_ids_size, method_ids_off = struct.unpack_from('<II', data, 88)

    # Read string table
    string_offsets = struct.unpack_from(f'<{string_ids_size}I', data, string_ids_off)
    strings = []
    for off in string_offsets:
        # utf16_size as uleb128
        pos = off
        length = 0
        shift = 0
        while True:
            b = data[pos]
            pos += 1
            length |= (b & 0x7f) << shift
            if (b & 0x80) == 0:
                break
            shift += 7
        # C string until \0
        end = data.find(b'\0', pos)
        try:
            strings.append(data[pos:end].decode('utf-8', errors='replace'))
        except:
            strings.append("")

    # Read type table (indexes into strings)
    type_ids = struct.unpack_from(f'<{type_ids_size}I', data, type_ids_off)
    types = [strings[idx] for idx in type_ids]

    # Read proto table: shorty_idx(I), return_type_idx(I), parameters_off(I)
    protos = []
    for i in range(proto_ids_size):
        shorty_idx, return_type_idx, params_off = struct.unpack_from('<III', data, proto_ids_off + i * 12)
        ret_type = types[return_type_idx]
        param_types = []
        if params_off != 0:
            param_count = struct.unpack_from('<I', data, params_off)[0]
            if param_count > 0:
                param_type_idxs = struct.unpack_from(f'<{param_count}H', data, params_off + 4)
                param_types = [types[idx] for idx in param_type_idxs]
        protos.append((ret_type, param_types))

    # Read method table: class_idx(H), proto_idx(H), name_idx(I)
    methods = []
    for i in range(method_ids_size):
        class_idx, proto_idx, name_idx = struct.unpack_from('<HHI', data, method_ids_off + i * 8)
        class_name = types[class_idx]
        method_name = strings[name_idx]
        ret_type, param_types = protos[proto_idx]
        methods.append((class_name, method_name, param_types, ret_type))

    return methods

all_methods = set()
dex_files = glob.glob(r"build\dex_cache\*.dex")
cs_methods = []

for df in dex_files:
    fname = os.path.basename(df)
    methods = parse_dex(df)
    for cls, name, params, ret in methods:
        if cls.startswith('Lcom/lagradost/'):
            cs_methods.append((fname, cls, name, tuple(params), ret))

# Group by (cls, name, params, ret)
from collections import defaultdict
grouped = defaultdict(list)
for fname, cls, name, params, ret in cs_methods:
    grouped[(cls, name, params, ret)].append(fname)

print(f"Total unique com.lagradost method references: {len(grouped)}")

# Check against AnimeciX specifically (23.dex)
print("\n--- Methods called by 23.dex (AnimeciX) on com.lagradost: ---")
for (cls, name, params, ret), files in grouped.items():
    if '23.dex' in files:
        params_str = ", ".join(params)
        print(f"23.dex -> {cls}->{name}({params_str}) -> {ret}")
