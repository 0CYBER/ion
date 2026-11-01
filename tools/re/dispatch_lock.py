"""Resolve the V12 command getters in the pinned INMOTION 8.1.34 ARM snapshot.

Pure static inspection: reads the local artifact, does not execute it or use BLE.
Requires capstone. Offsets and anchors are specific to the checked SHA-256.
Serialization: Dart SDK 2.19.1 app_snapshot.cc ReadDispatchTable; ARM register
origin: runtime/vm/dispatch_table.h kOriginElement = 1023.
"""
import argparse
import hashlib
import io
import struct
from pathlib import Path
from capstone import Cs, CS_ARCH_ARM, CS_MODE_ARM

SHA = '92a24d380f129750f4893e92e823b878db531dee5483936b1d66c07d015263f6'


def unsigned(stream):
    result = shift = 0
    while True:
        raw = stream.read(1)
        if not raw:
            raise ValueError('Unexpected end of stream')
        byte = raw[0]
        result |= (byte & 127) << shift
        if byte & 128:
            return result
        shift += 7
        if shift > 70:
            raise ValueError('Invalid integer')


def signed(stream):
    result = shift = 0
    while True:
        byte = stream.read(1)[0]
        if byte & 128:
            return result | ((byte - 192) << shift)
        result |= byte << shift
        shift += 7
        if shift > 70:
            raise ValueError('Invalid signed integer')


def ref(stream):
    value = 0
    while True:
        byte = stream.read(1)[0]
        value = (value << 7) | (byte & 127)
        if byte & 128:
            return value


def dispatch(data):
    stream = io.BytesIO(data)
    stream.seek(0x32905d)
    length, first_code = unsigned(stream), unsigned(stream)
    assert (length, first_code) == (110002, 127904)
    recent, recent_index, value, entries = [0] * 64, 0, 0, []
    while len(entries) < length:
        encoded = signed(stream)
        if encoded == 0:
            value = 0
        elif encoded < 0:
            assert ~encoded < 64
            value = recent[~encoded]
        elif encoded <= 63:
            entries.extend([value] * encoded)
            continue
        else:
            value = encoded - 64
            assert 1 <= value <= 46112
            recent[recent_index] = value
            recent_index = (recent_index + 1) & 63
        entries.append(value)
    snapshot_end = 0x6280 + 4 + struct.unpack_from('<q', data, 0x6284)[0]
    assert len(entries) == length and stream.tell() == snapshot_end == 0x33dbc5
    return entries


def classes(data):
    # Product FullAOT Class records: 13 refs, seven signed integers,
    # then unsigned unboxed bitmap unless this is a top-level class.
    # Anchor is the V12MessageFactory Class record; follow contiguous records.
    stream = io.BytesIO(data)
    stream.seek(0x250a4b)
    result = {}
    while stream.tell() <= 0x283b3c:
        start = stream.tell()
        refs = [ref(stream) for _ in range(13)]
        values = [signed(stream) for _ in range(7)]
        cid = values[0]
        if cid < 1048576:
            unsigned(stream)
        assert all(1 <= item <= 317129 for item in refs)
        if cid in (1760, 2108, 2109, 2110):
            result[cid] = (start, refs[0], values)
    assert result[1760][:2] == (0x250a4b, 87829)
    assert result[2108][:2] == (0x250a6d, 7610)
    assert result[2109][:2] == (0x283b3c, 56629)
    assert result[2110][:2] == (0x283b1b, 4731)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifact', type=Path)
    args = parser.parse_args()
    data = args.artifact.read_bytes()
    assert hashlib.sha256(data).hexdigest() == SHA, 'Unexpected artifact'
    entries = dispatch(data)
    records = classes(data)
    pcs = [0x95ce00 + struct.unpack_from('<I', data, 0x33dc5c + 8*i)[0]
           for i in range(46112)]
    md = Cs(CS_ARCH_ARM, CS_MODE_ARM)
    names = {1760: 'V12MessageFactory', 2108: '_V12Cmd (base)',
             2109: '_V12CmdV1', 2110: '_V12CmdV2'}
    print('Artifact SHA-256:', SHA)
    print('Dispatch: 110002 entries; exact snapshot-end validation passed')
    for cid, (offset, name_ref, values) in records.items():
        print(f'Class {names[cid]} cid={cid} file={offset:#x} name_ref={name_ref}')
    for origin in (1023, -1023, 0):
        print(f'\nOrigin candidate: {origin}')
        for label, displacement, expected in (
                ('lock (ci15273 dispatch at 0xde9088)', 0x63c5c, 0x31),
                ('headlight (ci20571 dispatch at 0xf3ce24)', 0x63af0,
                 {2109: 0x40, 2110: 0x50})):
            for cid in (2109, 2110):
                index = origin + displacement // 4 + cid
                code = entries[index]
                print(f'{label}: cid={cid}, table_index={index}, ci={code}')
                if not code:
                    continue
                start, end = pcs[code-1:code+1]
                instructions = list(md.disasm(data[start:end], start))
                for ins in instructions[:8]:
                    print(f'  {ins.address:#x} {ins.mnemonic} {ins.op_str}')
                if origin == 1023:
                    expected_value = expected[cid] if isinstance(expected, dict) else expected
                    assert instructions[0].mnemonic == 'mov'
                    assert instructions[0].op_str == f'r0, #{expected_value:#x}'
                    assert instructions[1].op_str == 'r1, #0'
                    assert instructions[2].mnemonic == 'bx'
    print('\nValidated +1023 origin: lock=0x31 for V12CmdV1/V2.')
    print('Abstract _V12Cmd and factory CIDs are not valid receiver substitutions.')
    print('This proves command values; model selection and bool polarity require their own traces.')


if __name__ == '__main__':
    main()
