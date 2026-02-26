"""检查各种导出文件的内容格式"""
import sys
import zipfile
import struct
import zlib
from pathlib import Path

if sys.stdout and hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

BASE = Path("爬取结果/计科23数据结构第1次实验/导出")

def decode_name(raw):
    try:
        return raw.encode('cp437').decode('gbk')
    except:
        return raw

def safe_read(zf, info):
    try:
        return zf.read(info)
    except zipfile.BadZipFile as e:
        if "differ" not in str(e):
            raise
        zf.fp.seek(info.header_offset)
        fh = zf.fp.read(30)
        fn_len, ex_len = struct.unpack('<HH', fh[26:30])
        zf.fp.read(fn_len + ex_len)
        data = zf.fp.read(info.compress_size)
        if info.compress_type == zipfile.ZIP_STORED:
            return data
        return zlib.decompress(data, -15)

# 1. PAPER_ANALYSIS.zip
print("=" * 60)
print("PAPER_ANALYSIS (答卷分析)")
print("=" * 60)
pa = list(BASE.glob("*PAPER_ANALYSIS*"))
if pa:
    with zipfile.ZipFile(pa[0]) as zf:
        for info in zf.infolist():
            name = decode_name(info.filename)
            print(f"  {name}  ({info.file_size} bytes)")
        # 读第一个非目录文件看内容
        for info in zf.infolist():
            if not info.is_dir() and info.file_size > 0:
                name = decode_name(info.filename)
                raw = safe_read(zf, info)
                text = raw.decode('utf-8', errors='replace')
                print(f"\n  --- 内容预览: {name} ---")
                print(text[:3000])
                break

# 2. PAPER_ACCURATE.xlsx
print("\n" + "=" * 60)
print("PAPER_ACCURATE (正答率)")
print("=" * 60)
acc = list(BASE.glob("*PAPER_ACCURATE*"))
if acc:
    try:
        import openpyxl
        wb = openpyxl.load_workbook(acc[0], read_only=True)
        ws = wb.active
        rows = list(ws.iter_rows(values_only=True))
        for i, row in enumerate(rows[:20]):
            print(f"  Row {i}: {row}")
        wb.close()
    except Exception as e:
        print(f"  Error: {e}")

# 3. PAPER.zip
print("\n" + "=" * 60)
print("PAPER (答卷)")
print("=" * 60)
pp = list(BASE.glob("*PAPER.zip"))
if pp:
    with zipfile.ZipFile(pp[0]) as zf:
        for info in zf.infolist()[:20]:
            name = decode_name(info.filename)
            print(f"  {name}  ({info.file_size} bytes)")
        # 读第一个 html
        for info in zf.infolist():
            if not info.is_dir() and info.filename.endswith('.html'):
                name = decode_name(info.filename)
                raw = safe_read(zf, info)
                text = raw.decode('utf-8', errors='replace')
                print(f"\n  --- 内容预览: {name} ---")
                print(text[:2000])
                break
