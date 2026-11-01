"""Extract only V12 lock request/reply frames from a private app logcat dump.

Never prints complete unrelated log lines or identifiers. Input remains private.
This does not establish success, timing causation, or applicability to other wheels.
"""
import argparse
import re
from pathlib import Path
from functools import reduce
from operator import xor


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path)
    args = parser.parse_args()
    pattern = re.compile(r'(?i)(?<![0-9a-f])aaaa[0-9a-f]{12,}(?![0-9a-f])')
    count = 0
    for line in args.log.read_text(encoding='utf-8', errors='replace').splitlines():
        if 'flutter' not in line.lower():
            continue
        match = pattern.search(line)
        if not match:
            continue
        frame = bytes.fromhex(match.group())
        if len(frame) != 10 or frame[:4] != bytes.fromhex('aa aa 16 05'):
            continue
        is_write = ('write characteristic' in line.lower() and
                    frame[4:8] == bytes.fromhex('02 21 60 31') and frame[8] in (0, 1))
        is_reply = ('bytes:' in line.lower() and
                    frame[4:9] == bytes.fromhex('21 02 e0 31 00'))
        if not (is_write or is_reply):
            continue
        if reduce(xor, frame[2:-1], 0) != frame[-1]:
            raise ValueError('lock frame has invalid checksum')
        timestamp = re.match(r'^\d\d-\d\d \d\d:\d\d:\d\d\.\d+', line)
        print(timestamp.group() if timestamp else '(no timestamp)',
              'write' if is_write else 'bytes', frame.hex(' ').upper())
        count += 1
    print('matched frames:', count)


if __name__ == '__main__':
    main()
