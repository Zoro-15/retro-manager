import struct
import sys
from pathlib import Path

# Android Binary XML Chunk Types
RES_NULL_TYPE = 0x0000
RES_STRING_POOL_TYPE = 0x0001
RES_TABLE_TYPE = 0x0002
RES_XML_TYPE = 0x0003
RES_XML_FIRST_CHUNK_TYPE = 0x0100
RES_XML_START_NAMESPACE_TYPE = 0x0100
RES_XML_END_NAMESPACE_TYPE = 0x0101
RES_XML_START_ELEMENT_TYPE = 0x0102
RES_XML_END_ELEMENT_TYPE = 0x0103
RES_XML_CDATA_TYPE = 0x0104
RES_XML_LAST_CHUNK_TYPE = 0x017f
RES_XML_RESOURCE_MAP_TYPE = 0x0180

def decode_string_pool(data, offset):
    chunk_type, header_size, size = struct.unpack_from("<HHI", data, offset)
    string_count, style_count, flags, strings_start, styles_start = struct.unpack_from("<IIIII", data, offset + 8)
    is_utf8 = bool(flags & (1 << 8))
    print(f"StringPool: count={string_count}, flags=0x{flags:x}, utf8={is_utf8}, size={size}")
    
    offsets = [struct.unpack_from("<I", data, offset + 28 + i * 4)[0] for i in range(string_count)]
    strings = []
    pool_base = offset + strings_start
    for o in offsets:
        str_offset = pool_base + o
        if is_utf8:
            # UTF-8: len1 (1-2 bytes), len2 (1-2 bytes), then null-terminated string
            b = data[str_offset]
            if b & 0x80:
                str_offset += 2
            else:
                str_offset += 1
            b = data[str_offset]
            if b & 0x80:
                str_offset += 2
            else:
                str_offset += 1
            # read until 0x00
            end = data.find(b"\x00", str_offset)
            s = data[str_offset:end].decode("utf-8", errors="replace")
        else:
            length = struct.unpack_from("<H", data, str_offset)[0]
            s = data[str_offset + 2:str_offset + 2 + length * 2].decode("utf-16le", errors="replace")
        strings.append(s)
    return strings, offset + size

def decode_axml(data):
    chunk_type, header_size, size = struct.unpack_from("<HHI", data, 0)
    print(f"AXML Header: chunk_type=0x{chunk_type:04x}, header_size={header_size}, total_size={size} (actual={len(data)})")
    if chunk_type != RES_XML_TYPE:
        print(f"ERROR: Not valid AXML! chunk_type is 0x{chunk_type:04x}")
        return

    offset = 8
    strings = []
    res_ids = []

    while offset < len(data):
        chunk_type, header_size, chunk_size = struct.unpack_from("<HHI", data, offset)
        chunk_name = f"0x{chunk_type:04x}"
        if chunk_type == RES_STRING_POOL_TYPE:
            print(f"\n[Chunk {chunk_name} String Pool @ {offset}, size={chunk_size}]")
            strings, next_offset = decode_string_pool(data, offset)
            for idx, s in enumerate(strings):
                if idx < 30 or "activity" in s.lower() or "retropack" in s.lower():
                    print(f"  [{idx}] {s}")
            offset = next_offset
            continue
        elif chunk_type == RES_XML_RESOURCE_MAP_TYPE:
            print(f"\n[Chunk {chunk_name} Resource Map @ {offset}, size={chunk_size}]")
            count = (chunk_size - header_size) // 4
            res_ids = [struct.unpack_from("<I", data, offset + header_size + i * 4)[0] for i in range(count)]
            print(f"  Resource IDs count={count}: {[hex(x) for x in res_ids[:15]]}")
        elif chunk_type == RES_XML_START_NAMESPACE_TYPE:
            line_num, comment_idx, prefix_idx, uri_idx = struct.unpack_from("<IIII", data, offset + 8)
            prefix = strings[prefix_idx] if prefix_idx != 0xFFFFFFFF and prefix_idx < len(strings) else ""
            uri = strings[uri_idx] if uri_idx != 0xFFFFFFFF and uri_idx < len(strings) else ""
            print(f"\n[Start Namespace] prefix='{prefix}' uri='{uri}'")
        elif chunk_type == RES_XML_END_NAMESPACE_TYPE:
            print(f"[End Namespace]")
        elif chunk_type == RES_XML_START_ELEMENT_TYPE:
            line_num, comment_idx = struct.unpack_from("<II", data, offset + 8)
            ns_idx, name_idx, attr_start, attr_size, attr_count, id_idx, class_idx, style_idx = struct.unpack_from("<IIHHHHHH", data, offset + 16)
            elem_name = strings[name_idx] if name_idx < len(strings) else f"str_{name_idx}"
            print(f"\n<Start Element: '{elem_name}' attrs={attr_count} (attr_start={attr_start}, attr_size={attr_size})>")
            
            # Attributes (attr_start is relative to the start of ResXMLTree_attrExt, which is offset + 16)
            attr_offset = offset + 16 + attr_start
            for a in range(attr_count):
                entry_off = attr_offset + a * attr_size
                a_ns, a_name, a_raw_val = struct.unpack_from("<III", data, entry_off)
                v_size, v_res0, v_type, v_data = struct.unpack_from("<HBB I", data, entry_off + 12)
                
                ns_str = strings[a_ns] if a_ns != 0xFFFFFFFF and a_ns < len(strings) else ""
                name_str = strings[a_name] if a_name < len(strings) else f"str_{a_name}"
                raw_str = strings[a_raw_val] if a_raw_val != 0xFFFFFFFF and a_raw_val < len(strings) else ""
                
                res_id_str = f" [res_id=0x{res_ids[a_name]:08x}]" if a_name < len(res_ids) else ""
                print(f"    attr: {ns_str}:{name_str}{res_id_str} = '{raw_str}' [type=0x{v_type:02x}, data=0x{v_data:08x}]")
        elif chunk_type == RES_XML_END_ELEMENT_TYPE:
            line_num, comment_idx, ns_idx, name_idx = struct.unpack_from("<IIII", data, offset + 8)
            elem_name = strings[name_idx] if name_idx < len(strings) else f"str_{name_idx}"
            print(f"</End Element: '{elem_name}'>")
        else:
            print(f"[Chunk type=0x{chunk_type:04x} size={chunk_size}]")

        offset += chunk_size

if __name__ == "__main__":
    if len(sys.argv) > 1:
        data = Path(sys.argv[1]).read_bytes()
        decode_axml(data)
    else:
        print("Usage: py test_axml_parse.py <path_to_axml>")
