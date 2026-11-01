"""Verify dispatch in the phone's INMOTION 8.1.34 ARM64 libapp.so.

Static only; no execution, BLE, or modification of the input. Requires capstone.
Offsets are file offsets for the pinned SHA, NOT memory addresses. Class/name
anchors come from checked Dart 2.19.1 compressed-pointer snapshot streams.
"""
import argparse
import hashlib
import io
import struct
from pathlib import Path
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_ARM

SHA = '334d0cdba71abd205a550b952faa9541490170132fd7f1347b8d0efe0024f8da'

def unsigned(s):
    value = shift = 0
    while True:
        b = s.read(1)[0]
        if b & 128:
            return value | ((b - 128) << shift)
        value |= b << shift
        shift += 7
        assert shift < 70

def signed(s):
    value = shift = 0
    while True:
        b = s.read(1)[0]
        if b & 128:
            return value | ((b - 192) << shift)
        value |= b << shift
        shift += 7
        assert shift < 70

def ref(s):
    value = 0
    while True:
        b = s.read(1)[0]
        value = (value << 7) | (b & 127)
        if b & 128:
            return value

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifact', type=Path)
    args = parser.parse_args()
    data = args.artifact.read_bytes()
    assert hashlib.sha256(data).hexdigest() == SHA
    s = io.BytesIO(data)
    for off, expected in [(0x492c42, (87785, 1760)),
                          (0x4c79e8, (56605, 2109)),
                          (0x4c79c7, (4712, 2110))]:
        s.seek(off)
        refs = [ref(s) for _ in range(13)]
        values = [signed(s) for _ in range(7)]
        assert (refs[0], values[0]) == expected, (hex(off), refs[0], values[0])
        unsigned(s)
        print('class', hex(off), 'name_ref', refs[0], 'cid', values[0])
    for off, name, owner, ci in [(0x3ae3d0, 4165, 235494, 19477),
                                  (0x3ae3df, 4165, 235500, 19478),
                                  (0x3afeae, 69228, 235551, 19921)]:
        s.seek(off)
        refs = [ref(s) for _ in range(4)]
        actual_ci = unsigned(s)
        tag = signed(s)
        assert (refs[0], refs[1], actual_ci) == (name, owner, ci)
        print('function', hex(off), 'owner_ref', owner, 'ci', ci, 'tag', tag)
    s.seek(0x685750)
    length, first = unsigned(s), unsigned(s)
    assert (length, first) == (115054, 127829)
    recent, ri, value, entries = [0]*64, 0, 0, []
    while len(entries) < length:
        v = signed(s)
        if v == 0:
            value = 0
        elif v < 0:
            assert ~v < 64
            value = recent[~v]
        elif v <= 63:
            entries.extend([value]*v)
            continue
        else:
            value = v - 64
            assert 1 <= value <= 46054
            recent[ri] = value
            ri = (ri+1) & 63
        entries.append(value)
    assert len(entries) == length and s.tell() == 0x69a8d3
    print('dispatch entries', length, 'end', hex(s.tell()))

    assert struct.unpack_from('<I', data, 0x69a954)[0] == 46054
    pcs = [0x7c1980 + struct.unpack_from('<I', data, 0x69a960+8*i)[0]
           for i in range(46054)]
    assert pcs == sorted(pcs) and pcs[0] == 0x7c19e8
    for addr, code in [(0xcedf0c, '91f691d23100a0f21e00118bbe7a7ef8c0033fd6'),
                       (0xd3d9f8, '1e003ed1be7a7ef8c0033fd6')]:
        assert data[addr:addr+len(bytes.fromhex(code))] == bytes.fromhex(code)
    md = Cs(CS_ARCH_ARM64, CS_MODE_ARM)
    cases = [('lock V1', 4096+0x18fb4+2109, 0x31),
             ('lock V2', 4096+0x18fb4+2110, 0x31),
             ('headlight V1', 4096-0xf80+2109, 0x40),
             ('headlight V2', 4096-0xf80+2110, 0x50)]
    for label, slot, expected in cases:
        ci = entries[slot]
        start, end = pcs[ci-1], pcs[ci]
        ins = list(md.disasm(data[start:end], start))
        assert ins[0].mnemonic == 'mov' and ins[0].op_str == f'x0, #{expected:#x}'
        assert ins[1].mnemonic == 'ret'
        print(label, 'slot', slot, 'ci', ci, 'offset', hex(start), 'value', hex(expected))
    print('Static validation only: no runtime, packet, or wheel behavior asserted.')

if __name__ == '__main__':
    main()
