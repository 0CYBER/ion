"""Bounded Dart 2.19 ARM Class-record recovery for pinned INMOTION 8.1.34.
No target execution. Uses independently calibrated class-reference anchor;
parses sequential Class records, including bitmap-free top-level classes.
"""
from pathlib import Path
import argparse, hashlib, struct, io, json
SHA='92a24d380f129750f4893e92e823b878db531dee5483936b1d66c07d015263f6'

def unsigned(f):
    x=s=0
    for _ in range(12):
        b=f.read(1)[0]
        if b&128:return x|((b-128)<<s)
        x|=b<<s;s+=7
    raise ValueError('invalid unsigned integer')

def signed(f):
    x=s=0
    for _ in range(12):
        b=f.read(1)[0]
        if b&128:return x|((b-192)<<s)
        x|=b<<s;s+=7
    raise ValueError('invalid signed integer')

def ref(f):
    x=0
    for _ in range(8):
        b=f.read(1)[0];x=(x<<7)|(b&127)
        if b&128:return x
    raise ValueError('invalid reference')

class Classes:
    def __init__(self,path):
        self.data=Path(path).read_bytes()
        if hashlib.sha256(self.data).hexdigest()!=SHA:raise ValueError('unexpected artifact SHA-256')
        self.strings=self._strings()
        self.records={}
        # Anchor calibrated against 269 constructor-name/Function owner pairs.
        f=io.BytesIO(self.data);f.seek(0x24fdf6);rid=235998
        while f.tell()<0x283b80:
            off=f.tell();refs=[ref(f) for _ in range(13)];v=[signed(f) for _ in range(7)]
            bitmap=unsigned(f) if v[0]<1048576 else None
            self.records[rid]={'ref':rid,'offset':hex(off),'end':hex(f.tell()),'name':self.strings.get(refs[0]),'cid':v[0],
                'instance_words':v[1],'next_field_words':v[2],'type_arguments_offset_words':v[3],
                'num_type_arguments':v[4],'native_fields':v[5],'state_bits':v[6],'unboxed_bitmap':bitmap,'refs':refs,
                'super_type_ref':refs[9],'own_type_ref':refs[11]}
            rid+=1
        for rid,name,cid in [(235998,'InMotionProtocol',1995),(236034,'LorinMessageFactory',1767),
                (236040,'SpecialMessageFactory',1745),(236093,'V12MessageFactory',1760),(236531,'EzcanFactory',2473)]:
            assert self.records[rid]['name']==name and self.records[rid]['cid']==cid,(rid,self.records[rid])
    def _strings(self):
        d=self.data;f=io.BytesIO(d);f.seek(0x6280+52)
        while f.read(1)!=b'\0':pass
        base,total,ncl,ntable,ro=[unsigned(f) for _ in range(5)]
        assert signed(f)==185
        n=unsigned(f);out={};off=0
        for rid in range(base+1,base+n+1):
            off+=unsigned(f)<<3
            p=0x33dc00+off
            length=struct.unpack_from('<I',d,p+8)[0]//2
            out[rid]=d[p+12:p+12+length].decode('latin1')
        return out

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('artifact');p.add_argument('--ref',type=int,nargs='+');p.add_argument('--cid',type=int,nargs='+');p.add_argument('--all',action='store_true')
    a=p.parse_args();c=Classes(a.artifact)
    records=[r for r in c.records.values() if a.all or r['ref'] in (a.ref or [235998,236029,236034,236040,236093,236094,236531]) or r['cid'] in (a.cid or [2109,2110])]
    print(json.dumps({'artifact_sha256':SHA,'coverage':'bounded sequential Class records; static only','records':records},ensure_ascii=False,indent=2))
