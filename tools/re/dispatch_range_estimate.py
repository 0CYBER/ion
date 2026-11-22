"""Resolve the V12 remaining-range-estimate command byte in the owner's ARM64 INMOTION build.

Pure static inspection: reads the local libapp.arm64.so and the decoded dispatch table
(arm64_dispatch.pkl from the research root's arm64_dispatch_probe.py). Does not run the app or use BLE.
Requires capstone.

EucLorinSpecialMessageFactory.genRemainderRangeEstimateOnOffMsg loads its _V12Cmd object and
calls the getter through the dispatch table: table[cid + selector + 4096]. The same lookup is checked
against two command bytes Ion already uses and the wheel confirms (lock 0x31, sound wave 0x39).
"""
import argparse
import hashlib
import pickle
import struct
from pathlib import Path
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_ARM

SHA = '334d0cdba71abd205a550b952faa9541490170132fd7f1347b8d0efe0024f8da'
CODE_COUNT, PC_TABLE, CODE_BASE = 46054, 0x69a960, 0x7c1980
ORIGIN = 4096
V12_CMD_CIDS = (2109, 2110)  # _V12CmdV1, _V12CmdV2
# Selector displacements read from the factory stubs (mov x17 / movk x17, #1, lsl #16).
SELECTORS = {
    'lock (known 0x31)': (0x18fb4, 0x31),
    'soundWave (known 0x39)': (0x19331, 0x39),
    'remainderRangeEstimate': (0x19343, None),
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifact', type=Path)
    parser.add_argument('dispatch_pickle', type=Path)
    args = parser.parse_args()
    data = args.artifact.read_bytes()
    assert hashlib.sha256(data).hexdigest() == SHA, 'Unexpected artifact'
    entries = pickle.loads(args.dispatch_pickle.read_bytes())
    pcs = [CODE_BASE + struct.unpack_from('<I', data, PC_TABLE + 8 * i)[0] for i in range(CODE_COUNT)]
    md = Cs(CS_ARCH_ARM64, CS_MODE_ARM)
    for label, (selector, expected) in SELECTORS.items():
        for cid in V12_CMD_CIDS:
            slot = cid + selector + ORIGIN
            code = entries[slot]
            start, end = pcs[code - 1:code + 1]
            ins = list(md.disasm(data[start:end], start))
            assert ins[0].mnemonic == 'mov' and ins[1].mnemonic == 'ret', ins[:2]
            value = int(ins[0].op_str.split('#')[1], 16)
            if expected is not None:
                assert value == expected, (label, hex(value))
            print(f'{label}: cid={cid} slot={slot} ci={code} -> {value:#04x}')
    print('Static result only: polarity and read-back are confirmed by the settings byte40 bit6 diff.')


if __name__ == '__main__':
    main()
