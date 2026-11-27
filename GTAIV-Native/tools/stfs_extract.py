#!/usr/bin/env python3
"""Extract the files of an Xbox 360 STFS (CON/LIVE/PIRS) package.
usage: stfs_extract.py <package> <out dir>"""
import hashlib, os, struct, sys

def extract(pkg, out):
    d = open(pkg, "rb").read()
    assert d[:4] in (b"CON ", b"LIVE", b"PIRS"), "not an STFS package"
    header_size = struct.unpack_from(">I", d, 0x340)[0]
    vd = 0x379
    block_sep = d[vd + 2]
    ft_blocks = struct.unpack_from("<H", d, vd + 3)[0]
    ft_start = int.from_bytes(d[vd + 5:vd + 8], "little")
    sex = (~block_sep) & 1
    base = (header_size + 0xFFF) & 0xFFFFF000

    def backing(b):
        r = (((b + 0xAA) // 0xAA) << sex) + b
        if b < 0xAA: return r
        if b < 0x70E4: return r + (((b + 0x70E4) // 0x70E4) << sex)
        return (1 << sex) + r + (((b + 0x70E4) // 0x70E4) << sex)

    step0 = 0xAC if sex else 0xAB

    def hash_table_block(b):  # backing block of the level-0 hash table covering b
        n = (b // 0xAA) * step0
        if b >= 0xAA:
            n += ((b // 0x70E4) + 1) << sex
            if b >= 0x70E4: n += 1 << sex
        return n

    def next_block(b, data):
        # male packages keep two copies of each hash table: use the one whose
        # SHA-1 matches this block's data.
        t = base + (hash_table_block(b) << 12) + (b % 0xAA) * 0x18
        for copy in ((0, 0x1000) if sex else (0,)):
            ent = d[t + copy:t + copy + 0x18]
            if ent[:20] == hashlib.sha1(data).digest():
                return int.from_bytes(ent[0x15:0x18], "big")
        raise ValueError(f"hash mismatch at block {b}")

    def addr(b): return base + (backing(b) << 12)

    entries = []
    for i in range(ft_blocks):
        blk = d[addr(ft_start + i):addr(ft_start + i) + 0x1000]
        for e in range(0, 0x1000, 0x40):
            name = blk[e:e + 0x28].split(b"\0")[0]
            if not name: continue
            flags = blk[e + 0x28]
            nblocks = int.from_bytes(blk[e + 0x29:e + 0x2C], "little")
            start = int.from_bytes(blk[e + 0x2F:e + 0x32], "little")
            size = struct.unpack_from(">I", blk, e + 0x34)[0]
            entries.append((name.decode(), flags, nblocks, start, size))
    os.makedirs(out, exist_ok=True)
    for name, flags, nblocks, start, size in entries:
        if flags & 0x80: continue  # directory
        chunks = []; b = start
        for k in range(nblocks):
            blk = d[addr(b):addr(b) + 0x1000]
            chunks.append(blk)
            nb = next_block(b, blk)
            b = start + k + 1 if flags & 0x40 else nb
        data = b"".join(chunks)[:size]
        open(os.path.join(out, name), "wb").write(data)
        print(name, size)

if __name__ == "__main__":
    extract(sys.argv[1], sys.argv[2])
