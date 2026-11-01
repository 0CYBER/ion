import re,pathlib,datetime,sys,functools,operator
if len(sys.argv) < 2: raise SystemExit('usage: compare_security_sessions.py private-logcat [private-logcat ...]')
pat=re.compile(r'(?i)(?<![0-9a-f])aaaa[0-9a-f]{12,}(?![0-9a-f])')
def parse(path):
    pending=[]; cases=[]
    for line in path.read_text(encoding='utf-8',errors='replace').splitlines():
        m=pat.search(line)
        if not m or 'flutter' not in line.lower(): continue
        f=bytes.fromhex(m.group()); w='write characteristic' in line.lower(); r='bytes:' in line.lower()
        if not (w or r): continue
        stamp=re.search(r'\d\d-\d\d (\d\d:\d\d:\d\d\.\d+)',line)
        if not stamp: continue
        t=datetime.datetime.strptime(stamp.group(1),'%H:%M:%S.%f')
        if (w and (f.startswith(bytes.fromhex('aaaa130402000002')) or f.startswith(bytes.fromhex('aaaa131402000082')))) or (r and (f.startswith(bytes.fromhex('aaaa1213028002')) or f.startswith(bytes.fromhex('aaaa1204028082')))):
            pending.append((t,w,f));pending=pending[-8:]
        if w and len(f)==10 and f[:8]==bytes.fromhex('aaaa160502216031'):
            recent=[x for x in pending if 0 <= (t-x[0]).total_seconds() < 2]
            cases.append((t,f[8],recent));pending=[]
    return cases
allcases=[]
for name in sys.argv[1:]:
    cases=parse(pathlib.Path(name)); print(name,'lock actions',len(cases))
    for t,target,seq in cases:
        fs=[x[2] for x in seq]
        r=[f for f in fs if f.startswith(bytes.fromhex('aaaa1213028002'))]; w=[f for f in fs if f.startswith(bytes.fromhex('aaaa131402000082'))]; print(t.time(),'target',target,'lengths',[len(f) for f in fs],'direction',[('W' if x[1] else 'R') for x in seq], 'echoes',[bool(r) and r[-1][7:23]==q[8:24] for q in w], 'checksum_ok',all(functools.reduce(operator.xor,f[2:-1],0)==f[-1] for f in fs)); allcases.extend(z[7:23] for z in r)

print('comparable samples',len(allcases),'identical_across_all',bool(allcases) and all(x==allcases[0] for x in allcases))
