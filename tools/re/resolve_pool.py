"""Resolve ARM32 Dart AOT global-pool slots in the exact INMOTION 8.1.34 build.

Static only. The artifact hash and known stream boundaries are enforced.
Pool layout is ARM32: 4-byte object tags, 4-byte length, 4-byte entries.
PP is tagged, so index = (displacement + 1 - 8) // 4.
Does not execute the APK or the native image. Requires only Python stdlib.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path

SHA256 = '92a24d380f129750f4893e92e823b878db531dee5483936b1d66c07d015263f6'

class Reader:
    def __init__(self, data, pos=0):
        self.data, self.pos = data, pos
    def byte(self):
        value = self.data[self.pos]
        self.pos += 1
        return value
    def unsigned(self):
        value = shift = 0
        while True:
            b = self.byte()
            if b & 128:
                return value | ((b-128) << shift)
            value |= b << shift
            shift += 7
            assert shift < 70
    def signed(self):
        value = shift = 0
        while True:
            b = self.byte()
            if b & 128:
                return value | ((b-192) << shift)
            value |= b << shift
            shift += 7
            assert shift < 70
    def ref(self):
        value = 0
        while True:
            b = self.byte()
            value = (value << 7) | (b & 127)
            if b & 128:
                assert 0 < value <= 317129, value
                return value

class Artifact:
    def __init__(self, path):
        self.path = Path(path)
        self.data = data = self.path.read_bytes()
        self.sha256 = hashlib.sha256(data).hexdigest()
        if self.sha256 != SHA256:
            raise ValueError(f'Artifact SHA-256 mismatch: {self.sha256}')
        # Read the actual snapshot header, then String/Mint allocation clusters.
        snap = 0x6280
        assert struct.unpack_from('<I',data,snap)[0] == 0xdcdcf5f5
        r = Reader(data, data.index(b'\0', snap+52)+1)
        counts = [r.unsigned() for _ in range(5)]
        assert counts == [1087,317129,1055,46112,64], counts
        assert r.signed() == 185 # canonical String CID92
        n = r.unsigned()
        assert n == 89855
        offsets, running = [], 0
        for _ in range(n):
            running += r.unsigned() << 3
            offsets.append(running)
        table_length, prefix = r.unsigned(), r.unsigned()
        for _ in range(n-prefix):
            r.unsigned()
        self.strings = {}
        for ref, offset in enumerate(offsets, 1088):
            at = 0x33dc00 + offset
            tags, _, length = struct.unpack_from('<III', data, at)
            assert tags >> 12 in (93,94) and length % 2 == 0, (ref,hex(at),hex(tags),length)
            self.strings[ref] = data[at+12:at+12+length//2*(2 if tags>>12==94 else 1)].decode('utf-16-le' if tags>>12==94 else 'latin1', errors='surrogatepass')
        assert r.signed() == 121 # canonical Mint CID60
        n = r.unsigned()
        assert n == 22616
        self.ints = {ref:r.signed() for ref in range(90943,90943+n)}
        # This is the ObjectPool fill, independently anchored by length69024.
        r = Reader(data, 0x2bebac)
        assert r.unsigned() == 69024
        self.pool = []
        for i in range(69024):
            bits = r.byte()
            kind = bits & 127
            assert kind in (0,1,2,3,4), (i,kind)
            entry = {'index':i, 'kind':kind, 'patchable':bool(bits&128)}
            if kind == 0:
                entry['ref'] = r.ref()
            elif kind == 1:
                entry['immediate'] = r.signed()
            self.pool.append(entry)
        assert r.pos == 0x2fa17e, hex(r.pos)
        # Previously omitted canonical ImmutableArray fill: 2461 arrays.
        # First two lengths are 0, then5,5,7; parse all and check exact boundary.
        r = Reader(data, 0x1e78bc)
        self.arrays = {}
        lengths = []
        for ref in range(224573,224573+2461):
            length = r.unsigned()
            assert 0 <= length < 100000, length
            lengths.append(length)
            ta = r.ref()
            values = [r.ref() for _ in range(length)]
            self.arrays[ref] = {'type_arguments':ta, 'values':values}
        assert lengths[:5] == [0,0,5,5,7]
        assert r.pos == 0x2259b8, hex(r.pos)
        # Cross-check descriptor shape used by genMsg CI9469:
        # [type_args_len, positional_arg_count, size, count, null terminator].
        selected = self.arrays[224945]['values']
        assert selected == [923,91099,91445,91099,1], selected
        assert self.ints[91099] == 7 and self.ints[91445] == 8
        assert self.pool[self.index(0x97e7)]['ref'] == 224945
        assert self.strings[12562] == 'Low beam and high beam state should be set in the same time in V12.'
        # Canonical Type fill, after Double and TypeParameter. Nullability is
        # flags bits0..1, type-state bits2..3, class CID bits4..23.
        r = Reader(data, 0x6940c)
        for _ in range(518):
            r.signed() # Double raw bits encoded as signed64
        for _ in range(154):
            for k in range(3):
                r.ref()
            r.signed()
            for k in range(3):
                r.byte()
        assert r.pos == 0x6aef7
        self.types = {}
        for ref in range(114231,124930):
            refs = [r.ref() for _ in range(3)]
            flags = r.unsigned()
            self.types[ref] = {'refs':refs, 'flags':flags, 'class_id':(flags>>4)&0xfffff}
        assert r.pos == 0x84b30
        # Class fill: 13 object refs; cid, sizes, typeargs/native-field counts,
        # state; a bitmap for each non-top-level class. Product FullAOT omits
        # kernel and source-position members. The 122 predefined Class objects
        # precede regular classes in this same stream.
        r = Reader(data,0x2308d7)
        self.classes = {}
        for ref in range(232247,243772):
            start = r.pos
            refs = [r.ref() for _ in range(13)]
            values = [r.signed() for _ in range(7)]
            bitmap = r.unsigned() if values[0]<1048576 else None
            name = self.strings.get(refs[0])
            assert name is not None or refs[0]<=1087, (ref,refs[0])
            self.classes[ref] = {'ref':ref,'name':name,'name_ref':refs[0],
                'class_id':values[0],'refs':refs,'values':values,
                'unboxed_bitmap':bitmap,'offset':start,'end':r.pos,
                'super_type_ref':refs[9],'declaration_type_ref':refs[11]}
        assert r.pos == 0x28ee0c
        self.classes_by_cid = {c['class_id']:c for c in self.classes.values()}
        assert self.classes[236093]['name'] == 'V12MessageFactory'
        assert self.classes[236531]['name'] == 'EzcanFactory'
        assert self.classes[236034]['name'] == 'LorinMessageFactory'
        assert self.types[self.classes[236093]['declaration_type_ref']]['class_id'] == 1760
        for ref in [236093,236531,236034]:
            klass = self.classes[ref]
            assert self.types[klass['declaration_type_ref']]['class_id'] == klass['class_id']

    @staticmethod
    def index(displacement):
        offset = displacement + 1
        assert offset >= 8 and (offset-8)%4 == 0, displacement
        return (offset-8)//4
    def describe(self, ref, depth=1):
        if ref in self.ints:
            return {'ref':ref,'integer':self.ints[ref]}
        if ref in self.strings:
            return {'ref':ref,'string':self.strings[ref]}
        if ref in self.classes:
            c = self.classes[ref]
            supertype = self.types.get(c['super_type_ref'])
            superclass = self.classes_by_cid.get(supertype['class_id']) if supertype else None
            return {'ref':ref,'class':c['name'],'class_id':c['class_id'], 'super_type_ref':c['super_type_ref'], 'superclass': {'ref':superclass['ref'],'name':superclass['name'],'class_id':superclass['class_id']} if superclass else None, 'file_offset':hex(c['offset'])}
        if ref in self.types:
            t = self.types[ref]
            c = self.classes_by_cid.get(t['class_id'])
            return {'ref':ref,'type_class_id':t['class_id'],'class_name':c['name'] if c else None,'flags':t['flags']}
        if ref in self.arrays:
            arr = self.arrays[ref]
            return {'ref':ref,'array': [self.describe(x,depth-1) if depth else x for x in arr['values']], 'type_arguments':arr['type_arguments']}
        if ref == 1:
            return {'ref':ref,'base_object':'null'}
        return {'ref':ref,'unresolved':'base object or other allocation cluster'}
    def lookup(self, displacement):
        entry = dict(self.pool[self.index(displacement)])
        entry['pp_displacement'] = hex(displacement)
        if 'ref' in entry:
            entry['object'] = self.describe(entry['ref'])
        return entry

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifact', type=Path)
    parser.add_argument('--pp', type=lambda s:int(s,0), nargs='+', default=[0x97e7,0x1b,0x6b,0x53])
    parser.add_argument('--ref', type=int, nargs='*', default=[])
    args = parser.parse_args()
    artifact = Artifact(args.artifact)
    print(json.dumps({'artifact_sha256':artifact.sha256,'pool_layout':{'header':8,'entry_size':4,'tag':1}, 'slots':[artifact.lookup(x) for x in args.pp], 'references':[artifact.describe(x) for x in args.ref]},indent=2,ensure_ascii=False))

if __name__ == '__main__':
    main()
