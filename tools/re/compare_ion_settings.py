"""Compare two Ion snapshot SETTINGS payloads without revealing wheel identifiers.

Read-only, offline; output is NOT a BLE write capture. A changed bit alone does
not establish which user action caused it. Output omits full payloads/serials.
"""
import argparse
import json
from pathlib import Path


def load(path):
    doc = json.loads(Path(path).read_text(encoding='utf-8-sig'))
    records = doc.get('records')
    if not isinstance(records, list):
        raise ValueError(f'{path}: missing records')
    return {r.get('read'): r for r in records}


def payload(records, key):
    record = records.get(key)
    if not record or not isinstance(record.get('frame'), dict):
        return None
    value = record['frame'].get('payloadHex')
    return bytes.fromhex(value) if isinstance(value, str) else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('before')
    parser.add_argument('after')
    args = parser.parse_args()
    first, second = load(args.before), load(args.after)
    for key in ('MODEL', 'SERIAL'):
        a, b = payload(first, key), payload(second, key)
        status = 'unknown' if a is None or b is None else ('same' if a == b else 'different')
        print(f'{key}: {status} (values withheld)')
    a, b = payload(first, 'SETTINGS'), payload(second, 'SETTINGS')
    if a is None or b is None:
        raise ValueError('both snapshots must contain SETTINGS payloads')
    if len(a) != len(b):
        raise ValueError(f'SETTINGS lengths differ: {len(a)} vs {len(b)}')
    print(f'SETTINGS length: {len(a)} bytes')
    changes = [(i, x, y) for i, (x, y) in enumerate(zip(a, b)) if x != y]
    for i, x, y in changes:
        print(f'offset {i}: {x:02X} -> {y:02X}; XOR {x ^ y:02X}')
    print(f'changed bytes: {len(changes)}')
    if len(a) > 39:
        print(f'byte39 bit 0x20: {bool(a[39] & 0x20)} -> {bool(b[39] & 0x20)}')
    print('Snapshots are reads, not proof of transmitted lock/unlock bytes.')


if __name__ == '__main__':
    main()
