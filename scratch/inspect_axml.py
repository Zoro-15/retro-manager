import zipfile
import struct
from pathlib import Path

def inspect_apk(apk_path: Path):
    print(f"=== Inspecting {apk_path} ===")
    with zipfile.ZipFile(apk_path, "r") as zf:
        for info in zf.infolist():
            print(f"Entry: {info.filename}, compress_type={info.compress_type}, size={info.file_size}, compress_size={info.compress_size}, extra_len={len(info.extra)}")
            if info.compress_type == zipfile.ZIP_STORED and info.filename.startswith("lib/"):
                # Check alignment in header
                header_offset = info.header_offset
                print(f"  Header offset: {header_offset}")
        
        manifest_bytes = zf.read("AndroidManifest.xml")
        print(f"\nManifest size: {len(manifest_bytes)} bytes")
        magic = struct.unpack("<I", manifest_bytes[:4])[0]
        print(f"Magic: 0x{magic:08x} (Expected: 0x00080003 for Binary AXML)")
        if magic == 0x00080003:
            print("Valid AXML Magic!")
        else:
            print(f"NOT Binary AXML! First 100 bytes:\n{manifest_bytes[:100]}")

if __name__ == "__main__":
    runtimes_dir = Path("runtimes")
    for template in runtimes_dir.glob("*-unified/template.apk"):
        inspect_apk(template)
