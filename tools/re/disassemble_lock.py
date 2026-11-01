"""Reproduce bounded static ARM observations from INMOTION 8.1.34.
Requires Python 3 and capstone; does not execute the target or contact BLE.
Code indices/names are historical snapshot-parser results; owner/model dispatch
must be proved separately. Addresses below are FILE offsets, not ELF VAs.
"""
import argparse
import hashlib
import struct
from pathlib import Path
from capstone import Cs, CS_ARCH_ARM, CS_MODE_ARM

SHA = '92a24d380f129750f4893e92e823b878db531dee5483936b1d66c07d015263f6'
FUNCTIONS = {
    9467: 'EzcanFactory.genLocalStandardWriteMsg',
    9468: 'EzcanFactory.genLocalMsg', 9469: 'EzcanFactory.genMsg',
    9470: 'EzcanMessage.constructor', 9471: 'EzcanMessage.genCheck',
    9372: 'bool2Bytes', 9373: 'bool2Byte', 18392: 'facade.checkV12MsgFactory', 12988: 'EzcanFactory.genSetModeMsg',
    15271: 'lock', 15272: 'facade.genLockMsg',
    15273: 'one2one.genLockMsg', 39358: 'EzcanEncoder.msgToBytes',
    39359: 'EzcanEncoder.escape', 41474: 'EzcanFactory.genLockMsg',
}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifact', type=Path)
    parser.add_argument('--ci', type=int, nargs='+', default=list(FUNCTIONS))
    args = parser.parse_args()
    data = args.artifact.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != SHA:
        parser.error(f'Unexpected artifact SHA-256: {digest}')
    print(f'Artifact SHA-256: {digest}')
    print('Static ARM disassembly; no execution; file offsets only.')
    pcs = [0x95ce00 + struct.unpack_from('<I', data, 0x33dc5c + 8*i)[0]
           for i in range(46112)]
    if pcs[0] != 0x95ce54 or pcs != sorted(pcs):
        parser.error('InstructionsTable validation failed')
    md = Cs(CS_ARCH_ARM, CS_MODE_ARM)
    for ci in args.ci:
        if not 1 <= ci < len(pcs):
            parser.error(f'Code index out of range: {ci}')
        start, end = pcs[ci-1:ci+1]
        print(f'\nCI {ci} {FUNCTIONS.get(ci, "unresolved")} [{start:#x}, {end:#x})')
        for ins in md.disasm(data[start:end], start):
            print(f'{ins.address:#010x} {ins.bytes.hex():8s} {ins.mnemonic:8s} {ins.op_str}')

if __name__ == '__main__':
    main()
