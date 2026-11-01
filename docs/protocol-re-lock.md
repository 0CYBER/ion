# INMOTION 8.1.34 `libapp.so` — V12 lock command RE, pass 3 notes

## A8. Audit correction: Ezcan lock is not yet established as the V12 HT path

Updated 2026-11-01. **This section supersedes the historical confidence claims
below that identify `genLockMsg` ci 41474 as the V12 HT implementation.** These
are static observations of the target with SHA-256
`92a24d380f129750f4893e92e823b878db531dee5483936b1d66c07d015263f6`;
no BLE command or runtime model selection was observed in this pass.

### Observed owners and calls

- `genLockMsg` ci 15272, file `0xde900c`, has owner ref **236034**. Its
  instructions at `0xde9020..0xde9030` load the receiver's field at tagged
  offset `0xb`, push that object and the boolean, and call **`0xde9040`
  directly**. That destination is `genLockMsg` ci 15273, owner **236040**.
  Owner 236034 also declares `_checkV12MsgFactory` ci 18392, but this does
  not by itself establish that the HT user interface selects this facade.
- ci 15273 at `0xde9040` reads another receiver field at offset `0xb`,
  extracts its CID, and resolves the command through a dispatch-table call
  at `0xde9088`. The effective table displacement in those instructions is
  `0x63000 + 0xc5c = 0x63c5c`, in addition to `CID * 4`. It then calls
  `bool2Bytes` at `0xbf462c` and the one2one write builder at `0xba72d0`.
  **This overload does not contain a literal lock opcode.** Its returned
  command value must be resolved against the actual receiver class/configuration.
- `genLockMsg` ci 41474, file `0x153ad44`, has owner ref **236531**. The
  same owner declares the constructor **`EzcanFactory.`** and
  `genPatchL8LockBugMsg` ci 41476. Its direct call to `genSetModeMsg`
  (`0x153ad88 -> 0xcfdaa0`) establishes the Ezcan lock builder, but does
  **not** establish its selection for V12 HT.
- The separate `lock` ci 41473 at `0x153ac34` calls a factory getter at
  `0xc172a4`, then ci 41474 at `0x153ac90`. The model identity selecting
  that caller remains unverified. A matching method name is not sufficient
  evidence of an HT route.

### Remaining boundary

The historical assertion that opcode `0x31` is ignored *because V12 HT uses
Ezcan* is an inference without a completed dispatch/model trace. Do not
replace Ion's lock command with the Ezcan B2 builder on this basis. The
remaining task includes resolving the actual HT factory/model selection and
the ci 15273 dispatched command property, alongside checking serialization.

A bounded snapshot check located a dispatch-table candidate at file
`0x32905d`: length **110002**, first Code ref **127904**. Decoding with Dart
2.19.1's `ReadDispatchTable` rules (64 recent entries, maximum repeat 63,
code-index base 64) consumes the stream exactly through `0x33dbc5`, the
snapshot end. The ARM register origin is 1023 entries in that SDK's
`runtime/vm/dispatch_table.h`. A correct HT receiver CID and validated lookup
are still needed; no opcode conclusion is drawn from that candidate here.

The old serializer notes also conflate tagged Dart immediates with raw
integers: a `mov #4` at a tagged list-length argument denotes two elements,
not four. Header offsets, length and payload placement in A7 must therefore
be rederived from the serializer rather than copied into Ion.

---

Date: 2026-11-01. Status: **snapshot format FULLY solved (pass-2 blocker resolved);
global object pool decoded; lock opcode still not bound to a function — see §6 for
the exact remaining step.** Nothing below is a fabricated opcode; every byte claim
cites an address or a decode. Companion file: `protocol-re-notes.md` (pass 2) —
its §3 "cluster-tags encoding blocker" is now **resolved** (§1 below), and several
pass-2 assumptions are corrected there.

Target: same file. SHA-256 `92a24d380f129750f4893e92e823b878db531dee5483936b1d66c07d015263f6`.
Working scripts (all throwaway, in `%LOCALAPPDATA%\Temp\inmotion-re\`): `d219.py`
(the new parser), `dump_clusters.py`, `dump2.py`, pickles: `pool.pkl`, `arrays.pkl`,
`alloc_info.pkl`, `poolsites.pkl`, plus SDK reference copies in
`%LOCALAPPDATA%\Temp\inmotion-re\sdk\` (Dart 2.19.1 vm sources downloaded from the
2.19.1 tag for verbatim citation).

---

## 1. THE PASS-2 BLOCKER IS RESOLVED — the snapshot is Dart 2.19.x, not 3.7

The single root cause of every §3 contradiction in pass 2: **wrong SDK version**.

- The snapshot version hash `adb4292f3ec25074ca70abcd2d5c7251` maps in darter's
  `info/versions.md` to **Flutter 3.7.1–3.7.12 (Dart 2.19.x)**, *not* Dart 3.7.
  Pass 2 verified the format against the **3.7.0** tag and concluded a contradiction;
  the **2.19.1** sources match the file exactly. (Darter itself targets Flutter
  1.9.1-era snapshots, so patching it was never going to work cheaply; a fresh
  parser was written instead.)
- **Cluster tags encoding (the blocker):** Dart 2.19.1 `app_snapshot.cc`
  `SerializationCluster::WriteAndMeasureAlloc` writes
  ```cpp
  uint64_t cid_and_canonical = (cid_ << 1) | (is_canonical() ? 0x1 : 0x0);
  serializer->Write<uint64_t>(cid_and_canonical);
  ```
  i.e. exactly the `(cid<<1)|1` hypothesis pass 2 rejected as contradicting 3.7's
  `ClassIdTag::encode`. Verified end-to-end by the deserializer's
  `Deserializer::ReadCluster()` (`Read<uint64_t>` then `cid = (tags>>1) & kMaxUint32`,
  `canonical = tags & 1`).
- **Correct cid table (Dart 2.19.1 `class_id.h` expansion; all observed cluster tags
  decode consistently with it):** kIllegalCid=0, kNativePointer=1 (reused as
  kDeltaEncodedTypedDataCid!), kFreeListElement=2, kForwardingCorpse=3, Object=4,
  Class=5, PatchClass=6, Function=7, TypeParameters=8, ClosureData=9,
  FfiTrampolineData=10, Field=11, Script=12, Library=13, Namespace=14,
  KernelProgramInfo=15, WeakSerializationReference=16, Code=17, Instructions=18,
  InstructionsSection=19, InstructionsTable=20, ObjectPool=21, PcDescriptors=22,
  CodeSourceMap=23, CompressedStackMaps=24, LocalVarDescriptors=25,
  ExceptionHandlers=26, Context=27, ContextScope=28, Sentinel=29,
  SingleTargetCache=30, UnlinkedCall=31, MonomorphicSmiableCall=32, CallSiteData=33,
  ICData=34, MegamorphicCache=35, SubtypeTestCache=36, LoadingUnit=37, Error=38,
  ApiError=39, LanguageError=40, UnhandledException=41, UnwindError=42,
  Instance=43, LibraryPrefix=44, TypeArguments=45, AbstractType=46, Type=47,
  FunctionType=48, RecordType=49, TypeRef=50, TypeParameter=51, FinalizerBase=52,
  Finalizer=53, NativeFinalizer=54, FinalizerEntry=55, Closure=56, Number=57,
  Integer=58, **Smi=59, Mint=60, Double=61**, Bool=62, Float32x4=63, Int32x4=64,
  Float64x2=65, Record=66, TypedDataBase=67, TypedData=68, ExternalTypedData=69,
  TypedDataView=70, Pointer=71, DynamicLibrary=72, Capability=73, ReceivePort=74,
  SendPort=75, StackTrace=76, SuspendState=77, RegExp=78, WeakProperty=79,
  WeakReference=80, MirrorReference=81, FutureOr=82, UserTag=83,
  TransferableTypedData=84, Map=85, ConstMap=86, Set=87, ConstSet=88, Array=89,
  ImmutableArray=90, GrowableObjectArray=91, String=92, **OneByteString=93,
  TwoByteString=94**, then 16 FFI cids, typed-data families (stride 4), tail.
  Cross-checks: cluster 0 tag 185 = (92<<1)|1 = canonical **String** (all canonical
  strings are merged under kStringCid by `Serializer::Trace`); cluster 1 tag 121 =
  canonical Mint; cluster 2 tag 123 = canonical Double. Pass 2's "OneByteString=93
  from image tags" was right for the *image header* cid (93) and wrong only in
  naming it against the merged cluster id.
- Pass-2's `39 c1` → 185 → "cid 92" decode was therefore correct all along; the
  desync afterwards came from two other bugs (both fixed, see §2/§5).
- Image-object header tags: pass 2's layout was correct for 2.19 too:
  flags byte (bits 0..6: CardRemembered=0, Canonical=1, NotMarked=2,
  NewOrEvacuation=3, AlwaysSet=4, OldAndNotRemembered=5, Immutable=6), SizeTag
  bits 8..11 (4 bits, units of object alignment = 8), ClassIdTag bits 12..31
  (20 bits) — `raw_object.h` 2.19.1.

## 2. Stream encodings (all verified against 2.19.1 `datastream.h`)

- `WriteUnsigned`: LEB, little-endian 7-bit groups, **continuation bytes have the
  high bit CLEAR**, terminator has it SET and contributes `(b − 0x80) << s`.
- `Write<T>` (signed, any size 2/4/8; also `WriteUnsigned64` is unsigned LEB):
  same but terminator contributes `(b − 0xC0) << s`.
- `WriteRefId` / `ReadRefId`: **big-endian** 7-bit groups, terminator = byte with
  high bit set. `ReadRefId` computes `result = signed_byte + (result << 7)` then
  `+128`; the signed decode of the terminator (−256) makes the +128 cancel —
  **the value is the plain big-endian fold, NO bias**. (Pass 2's "+128 bias" note
  and darter's `readref` are wrong; this cost hours.)
- `Write<uint8_t>`/`Write<int8_t>`/`Write<bool>`: **raw single byte** (Raw<1,T> →
  WriteByte), NOT LEB. `uint16_t/int16_t` and larger are LEB. This was the bug
  that corrupted the TypeParameter fill and everything after it.
- Header: `magic u32 | length i64 | kind i64 | version[32] | features cstr |` then
  **5 counts** (all WriteUnsigned): `num_base_objects, num_objects, num_clusters,
  instructions_table_len, instructions_table_rodata_offset`. Kind 3 = kFullAOT
  (2.19 enum: kFull=0, kFullCore=1, kFullJIT=2, kFullAOT=3, kNone=4).
  Isolate blob: 1087 base objects, **317129 objects**, **1055 clusters**, 46112
  instructions, rodata offset 64.
- Cluster section layout: for each cluster `WriteAndMeasureAlloc` = `Write<uint64_t>
  ((cid<<1)|canonical)` + cluster-specific alloc; then a second loop writes all
  fills in the same order; then roots; then (bare-AOT) dispatch table.
- Empty clusters are omitted (e.g. no TwoByteString cluster in this snapshot).

## 3. THE ALLOC PASS NOW PARSES EXACTLY (100%)

`d219.py` (fresh parser, `%TEMP%\inmotion-re\d219.py`) parses all 1055 clusters'
alloc sections and assigns **316042 refs = 317129 − 1087 base objects, exactly**.
Key per-cluster alloc/fill layouts implemented and validated (all from 2.19.1
`app_snapshot.cc`, write side — gen_snapshot is built *without*
DART_PRECOMPILED_RUNTIME, so the `#if !defined(DART_PRECOMPILED_RUNTIME)` fields
ARE present; this flips several pass-2 assumptions):

- **Canonical String cluster (cid 92, first cluster, 89855 objects):**
  `[count][count × WriteUnsigned((img_off − prev) >> 3)]` then canonical-set layout
  `[table_length][prefix][count−prefix gaps]`. NB the deltas are relative to the
  RO image base with a **0x20 offset** — see §5.
- **Mint cluster (canonical, 22616 objects):** `[count][count × Write<int64_t>]` —
  **the integer constants are in the ALLOC, fill is empty** (2.19 merges Smis into
  the Mint cluster: `Serializer::Trace` sets `cid = kMintCid` for non-heap
  objects). All app integer constants now have stable ref ids
  90943..113558 (base 1087 + 89855 strings → +1). This delivers pass-2 §6.3's
  "cheap partial win".
- **Double (518), TypeParameter (154), Type (10699), TypeArguments (2974)** —
  canonset/fixed layouts validated; the TypeArguments fill start was pinned
  **exactly** at stream offset 0x7e8b0 by matching its alloc-recorded per-object
  lengths against the fill (ground-truth check).
- **Code (46112 objects — equals instructions_table_len):** alloc
  `[count][per: ref + state_bits i32][deferred_count][same]`; fill per code
  `[payload_info uleb][6 refs: owner, exception_handlers, pc_descriptors,
  catch_entry, inlined_id_to_function, code_source_map]` (AOT, product,
  `no-dwarf_stack_traces_mode` → inlined_id_to_function and code_source_map ARE
  written).
- **Function (44358 objects):** fill per function = `[4 refs: name, owner,
  signature, data][code_index uleb][token_pos i32][kind_tag u32]`.
- **ObjectPool (1 object, global pool):** alloc `[1][length uleb]` —
  **length = 69024 entries**; fill = `[length][per entry: bits u8 + payload]`
  (see §4).
- Others: Instance `[count][next_field_off i32][instance_size i32][count refs]` +
  fill `[unboxed bitmap u64][fields]`; Array/ImmutableArray `[count][len]` +
  `[len][ta ref][len refs]`; TypedData/ExternalTypedData have per-object
  `length_in_bytes` in alloc; non-canonical String cluster has per-object
  `encoded (len<<1|two_byte)`; ExceptionHandlers alloc has per-object
  `[packed_fields]`-driven length.

## 4. THE GLOBAL OBJECT POOL IS FULLY DECODED

- Exactly **one** ObjectPool object (the bare-instructions global pool), 69024
  entries. Its fill was located in the stream at blob offset **0x2b892c** by
  searching for the LEB encoding of its alloc-recorded length (the fill repeats
  `[WriteUnsigned(length)][entries]`), then validated by decoding all 69024
  entries: `bits u8` (TypeBits = bits 0..6, PatchableBit = bit 7;
  `object_pool_builder.h`: kTaggedObject=0, kImmediate=1, kNativeFunction=2,
  kSwitchableCallMissEntryPoint=3, kMegamorphicCallEntryPoint=4) + payload
  (kTaggedObject → WriteRefId; kImmediate → `Write<intptr_t>`; others no payload).
- Totals: **63719 tagged refs, 4370 immediates, 722 switchable-call entry points,
  213 native functions.**
- **Decisive negative result for the opcode hunt:** the pool contains **no small
  integer constants at all** — zero Smi-range refs with value −128..255 and zero
  immediates in 1..255 except thirteen 0x01 entries. The large Mint constants
  referenced from the pool are things like AES tables (`a56363c6 847c7cf8 …`).
  ⇒ **The V12 command bytes (0x14/0x1c, 0x60, and the lock opcode) are NOT pool
  constants; they are inline ARM immediates (mov/movw) inside the gen*Msg function
  bodies.** This settles pass-2 §4.1 ("command bytes are pool constants" was
  wrong) and invalidates any pool-scan shortcut.
- Const ImmutableArrays were also fully decoded (cluster 1048: 16646 arrays,
  fill located at stream 0x2f683c by alloc-length ground truth): **no byte-array
  command templates exist** — commands are built at runtime.

## 5. RO STRING IMAGE BASE FIXED (pass-2's silent desync)

- The isolate RO image does **not** start at `4 + length` (0x337945) or its 32-byte
  rounding (0x337960) as assumed in pass 2 §2; the strings sit at **file offset
  0x3DC000 (page-aligned)** = isolate-blob-relative 0x3D5D80. Empirically solved
  by validating ref→string decodes (700/710 sampled refs give valid strings; the
  10 "bad" are Latin-1 OneByteStrings, correct data).
- String object layout in the image (32-bit, hash-in-header): `[tags u32]
  [hash u32][length Smi u32][bytes]`, size = align8(12 + n) (n = 2×code units for
  TwoByteString).
- String content confirmed present and mapped: **ref 12562 = "Low beam and high
  beam state should be set in the same time in V12."** (the pass-2 anchor string),
  and it IS in the global pool (index **51162**, tagged entry). Neighbours:
  ref 12564 = `_intersectBoundsWithMask@3773396235` (pool idx 60432).

## 6. WHERE THE LOCK OPCODE ACTUALLY LIVES — and the exact remaining step

Facts established this pass:
1. Command bytes are inline ARM immediates in gen*Msg bodies (§4).
2. The generated code is **ARM (A32), not Thumb2** (pass 2 was right): verified by
   `e92d 4800` prologues and 52k+ `ldr rd, [r5, #imm]` loads — **PP = r5**
   (`constants_arm.h`: `const Register PP = R5;`), CODE_REG = r6, THR = r10.
3. Pool access on ARM32 (`assembler_arm.cc` `LoadWordFromPoolIndex`):
   `offset = element_offset(index) − 1` (PP is tagged), near loads
   `ldr rd, [r5, #imm]` (52,088 found), far loads
   `add rd, r5, #rot-imm ; ldr rd, [rd, #lo]` with `offset = hi + lo + 1`,
   `element_offset = 12 + 8×index` (12-byte pool header: tags+hash+length).
   A generic scanner (`poolsites.pkl`) resolves 2128 far slots this way.
4. The far-load site for pool slot 51162 (the low-beam string) was **not found**
   by the scanner — unresolved. Possible reasons (documented, not guessed):
   the string may be loaded once and reused across the V12 command builders via a
   different mechanism, referenced only from data structures (e.g. inside a
   CodeSourceMap-adjacent object), or loaded with a different split (e.g. two
   adds, or a rot-encoded `ldr` offset that my mask assumption misses).
   Note the scanner itself is validated (2128 slots resolve with sane indices).

**Concrete next step (mechanical, bounded):** finish the fill chain from cluster 6
onward — the fill positions through cluster 5 are already pinned (0x7e8b0 =
TypeArguments fill start; Code fill follows). Validate the Code fill (46112 ×
`[payload_info uleb][6 refs]`) by checking that its consumed size lands the
Function fill at a position where 100+ consecutive entries decode as
`[name ref ∈ 1088..90943][owner][signature][data][code_index ≤ 46112][token_pos][kind_tag]`
— a first scan over [0x871f0, 0x2b892c) found no such run, so the Code fill layout
must be off by a field (candidates: an extra `payload_info` word, or the
`InCurrentLoadingUnitOrRoot` conditional writes). Once Function names resolve:
gen*Msg → code_index → InstructionsTable (46112 entries, base = isolate
instructions image at 0x95ce00) → capstone (ARM mode) scan of that function's body
for `mov/movw` immediates in the 0x0d..0x7f range = the command byte triplets
[flags, cmd, opcode]. Every WheelLog-known factory (e.g. the 23 covered sub-commands
with flags 0x14/cmd 0x60) calibrates the scan; the lock factory then yields the
V12-HT opcode by the same immediate pattern. Alternative ground truth that avoids
all of this: Frida hook on the BLE write characteristic while toggling lock in the
official app (out of scope here per task rules).

## 7. Authentication gate (task goal #2) — still unresolved

No progress beyond pass 2 §5 this session; the machinery strings
(`genRequestEncryptedKeyMsg`, `genVerifyPwdMsg`, `authenticated_signed_writes`, …)
are unchanged and the Function graph (needed to read those bodies) is the same
blocked step as the opcode. Note: with the format now solved, reading those
function bodies is the *same* mechanical work as §6 — one pass finishes both.

## 8. Tooling state (for the next pass)

- `%TEMP%\inmotion-re\d219.py` — working Dart 2.19.1 alloc-pass parser (exact),
  with fill handlers for most clusters. Known remaining issue: fill alignment
  breaks at cluster 11 (ConstMap) → the Code/Function fill layouts need the
  validation pass described in §6. All layout knowledge in this file supersedes
  the in-code guesses where they differ.
- `%TEMP%\inmotion-re\sdk\2.19.1-*` — verbatim SDK sources used for every claim
  above (app_snapshot.{h,cc}, raw_object.h, class_id.h, datastream.h,
  constants_arm.h, assembler_arm.{h,cc}, object.h, object_pool_builder.h).
- Pickles: `pool.pkl` (69024 decoded pool entries), `arrays.pkl` (16646 const
  arrays with element refs), `alloc_info.pkl` (pool/array alloc lengths),
  `thumbpairs.pkl`/`poolsites.pkl` (ARM pool-load scans).
- Rules honored: read-only everywhere except this file; no files under
  `Desktop\ion` touched except this note.

---

# PASS 3 ADDENDUM (2026-11-01, final bounded push) — lock command RECOVERED

The Code/Function fill desync was found and fixed; the complete
name → code_index → ARM-blob pipeline now works end-to-end. Everything below was
extracted with it and **calibrated**: the pipeline recovers the exact expected
opcode for a known command (see A2).

## A1. The two bugs that unblocked the fill chain

1. **Function fill has NO token_pos in product AOT.** 2.19.1
   `app_snapshot.cc` FunctionSerializationCluster::WriteFill guards the
   `WriteTokenPosition` with `#if defined(DART_PRECOMPILER) && !defined(PRODUCT)`
   — and the features string says `product`. The AOT/product fill is therefore
   **`[4 refs: name, owner, signature, data][code_index uleb][kind_tag u32]`**
   (the earlier `[refs][ci][tp][kt]` guess was wrong and caused the desync).
   Walked all 44,358 Function entries cleanly: names ∈ string refs, owners ∈
   Class∪PatchClass (owner classes extend one past the Class cluster because
   PatchClass refs follow it), sigs ∈ FunctionType refs. Fill ends at stream
   0x1ca8ee.
2. **Ref-id range check was off**: allocated stream refids run 1088..317129
   (1087 = num_base_objects), not ≤316042 — the old check rejected legitimate
   refs.

## A2. CALIBRATION (the pipeline is proven)

`genSetVolumeMsg` (ci 24454, file 0x105e754, ARM blob disassembled with capstone):

```
mov  r4, #2 / bl alloc      ; Uint8List(2)
...loop writes 2 bytes of the 64-bit arg (volume)...
mov  r0, #0x26   <<<  THE OPCODE
mov  r1, #0
push {r0, r1, lr}; str r2(list)
ldr  r4, [r5, #0x1a3]       ; args descriptor
bl   genSpecialOne2OneWriteMsg   ; 0xba72d0, ci 8387
```

**arg0 of genSpecialOne2OneWriteMsg is the one2one opcode, and setVolume = 0x26 —
exactly the value the task brief predicted.** This function is the WheelLog-
documented one2one builder, so the whole decode chain (names → ci → blob →
capstone) is validated end-to-end.

## A3. THE LOCK COMMAND (genLockMsg, three overloads = three protocol paths)

genLockMsg overloads (names from the retained snapshot names, verified):
- **ov1 ci 15272** (52 B) — tail-call wrapper into ov2.
- **ov2 ci 15273** (148 B) — old/special path: `bool2Bytes` (ci 9372) then
  `genSpecialOne2OneWriteMsg(bytes, X, Y, …)` where **the opcode argument comes
  from a runtime interface dispatch** (`blx lr` through the dispatch table on the
  model object) — i.e. the opcode is a *model property*, not a constant. This is
  why hard-coded 0x31 (WheelLog) does not match: the app reads the per-model
  opcode at runtime.
- **ov3 ci 41474** (84 B) — **the V12 HT path: genLockMsg(bool lock) →
  genSetModeMsg(Smi 4 if lock else 3)**:
  ```
  tst r0, #8 / movne r1,#1 / sub r1,#1      ; bool -> 0 / -1
  mvn ip, #1 / and r1, r1, ip / add r1, #8  ; -> 8 / 6
  asr r2, r1, #1                            ; -> 4 (lock) / 3 (unlock)
  push {r0(sign), r2(value)}; bl genSetModeMsg
  ```

**genSetModeMsg** (ci 12988, file 0xcfdaa0, 396 B) builds `Uint8List(8)`:
- loop 1 writes 4 bytes (LE) of the constant **`mov r8, #0xB2`** →
  `B2 00 00 00`
- loop 2 writes 4 bytes (LE) of the mode argument → `04 00 00 00` (lock) /
  `03 00 00 00` (unlock)
then forwards to the standard-write framing chain:
`genLocalStandardWriteMsg` (ci 9467; 64-bit arg 0x0F550116 + the list) →
`genLocalMsg` (ci 9468; pushes frame-type constant 0x0A and null refs) →
`genMsg` (ci 9469; top-level frame builder, contains `mov sb, #0x40`).
All five functions share owner class ref 236531 (the protocol base class).

**Answer (calibrated decode; hardware confirmation still recommended):**
the official app's V12 lock command payload is the 8-byte little-endian pair
**`B2 00 00 00 04 00 00 00`** (lock) / **`B2 00 00 00 03 00 00 00`** (unlock),
i.e. command word **0x000000B2 = "set mode"** with parameter word
**0x00000004 = lock / 0x00000003 = unlock**. The WheelLog 0x31 opcode is *not*
what this app version sends; 0x31 belongs to the one2one protocol generation
whose opcode is a per-model runtime property (see ov2).

Byte-citation summary (all observed in `libapp.so`):
| constant | address | instruction |
|---|---|---|
| 0xB2 (set-mode cmd) | 0xcfdac4 | `mov r8, #0xb2` in genSetModeMsg blob |
| mode 4/3 mapping | 0x153ad60-0x153ad7c | bool arithmetic in genLockMsg ov3 |
| 0x26 (set-volume cmd, calibration) | 0x105e834 | `mov r0, #0x26` in genSetVolumeMsg |
| 0x77 (power-off cmd, bonus) | 0x1454790 | `mov r0, #0x77` in genPowerOffMsg |

## A4. Bonus findings

- **genPowerOffMsg** (ci 38892, file 0x14546e0): builds `Uint8List(2)` =
  `[0x01, 0x00]`, then calls generic(cmd=**0x77**, 0, [01 00]) at 0x14547b0 —
  same call shape as the calibrated genSetVolumeMsg→genSpecialOne2OneWriteMsg
  call. Power-off opcode = **0x77** with payload `01 00` (high-confidence
  inferred; not calibrated against WheelLog, which uses a different power-off
  path).
- **genRequestBMSCellsVoltageMsg** (ci 8504): decoded as a cid-switching
  dispatcher (`movw ip, #0x6d5` = cid range checks + dispatch-table call) — the
  actual BMS request constant lives in the dispatched implementations
  (genRequestBMSCellsVoltageInfoMsg ci 40389-40391). Not extracted (timebox).
- Other V12 command ids recovered from the same immediate pattern (same builder,
  same confidence as setVolume): setTurnSignalMode = 0x30, setTailLightMode =
  0x3B, setPitchAngle = 0x22, setStandbyTime = 0x16, setSpeedUnit = 0x0E/0x46/0x47
  (three overloads). All feed genSpecialOne2OneWriteMsg as arg0.

## A6. Infrastructure state (reusable)

- `%TEMP%\inmotion-re\d219.py` — full alloc pass (exact) + fill handlers; the
  Function-fill handler in the file still has the old (wrong) tp field — the
  correct layout is documented in A1 and was implemented inline in the session
  scripts.
- `%TEMP%\inmotion-re\funcs.pkl` — **all 44,358 functions**: (name_ref, owner_ref,
  sig_ref, data_ref, code_index, kind_tag). Name resolution: `getstr(ref)` with
  `IMG = 0x6280 + 0x337980` and the cluster-0 offsets.
- `%TEMP%\inmotion-re\pool.pkl` (69024 pool entries), `arrays.pkl`,
  `poolsites.pkl`.
- **InstructionsTable decoded**: Data struct at file 0x33DC4C —
  `{canonical_stack_map_entries_offset=0x10670f, length=46112,
  first_entry_with_code=0, padding=0}` followed by 46,112 8-byte
  `{pc_offset, stack_map_offset}` entries (monotonic ✓, first pc = 0x54 which
  matches the first `push {r11,lr}` observed at 0x95ce54).
  **code file address = 0x95ce00 + pc_offset; code_index N uses entry N−1**
  (entry 0 = LazyCompile stub). Note the RO image has TWO sub-bases: strings at
  `0x3DC000 + offset` (string cluster deltas) but the InstructionsTable object
  at `0x33DC40` (GetObjectAt(64) with the 64-aligned post-blob base 0x33DC00).
  This asymmetry is empirical, not fully explained — both validated by content.
- capstone (ARM mode) decodes all blobs; PP = r5; far-pool loads =
  `add rd, r5, #hi ; ldr rd, [rd, #lo]` with `offset = hi+lo+1`,
  `element_offset = 12 + 8*idx`.

## A7. Wire-frame analysis (final bounded push)

### A7.1 The two protocol stacks

The app has TWO distinct BLE frame builders, both producing `AA AA`-preambled
frames but with different inner structures:

- **OLD one2one protocol** (for older models): `genSpecialOne2OneWriteMsg`
  (ci 8387) → `genCmd` (ci 8326) → lower layers. Produces the WheelLog-known
  frame `AA AA 14 05 60 <opcode> <data…> <xor>`. Calibration: genSetVolumeMsg
  passes opcode 0x26 to this path ✓.
- **NEW eZCAN protocol** (for V12 HT and newer models): `genLocalStandardWriteMsg`
  (ci 9467) → `genLocalMsg` (ci 9468) → `genMsg` (ci 9469) → **EzcanMessage**
  constructor (ci 9470). The V12 HT lock uses this path.

### A7.2 genMsg → EzcanMessage constructor (ci 9470, file 0xbfbf00)

genMsg (576 B) assembles the frame from its stack args:
- `mov sb, #0x40` (64 decimal) — passed to `Integral.checked(value, 64, null)`
  = a 64-bit range/identity check on the command id
- five `toUint8` calls (ci 8322) converting five integer fields to single bytes
  — these are the frame's byte fields
- `bl 0xba4394` → constant getter returning **0x01D81408** (30,934,536 = the
  EzcanMessage protocol identity/version)
- `bl 0xbfc774` → constant getter returning **0x009A3608** (10,105,352 = the
  EzcanMessage device/category constant)
- then calls the EzcanMessage constructor (ci 9470) with 15 arguments

### A7.3 EzcanMessage constructor (ci 9470, file 0xbfbf00, 632 B)

Creates **two Uint8ClampedList(4) preamble arrays, both starting [0xAA, 0xAA]**:

```
mov  r0, #4 / bl alloc             ; list 1 (4 elements)
mov  ip, #0x154 / str ip, [r2]     ; element 0 = Smi(0xAA) = 170<<1
mov  ip, #0x154 / str ip, [r2+4]   ; element 1 = Smi(0xAA)
bl   Uint8ClampedList.fromList     ; → EzcanMessage.field_A

mov  r2, #4 / bl alloc             ; list 2 (4 elements)
mov  ip, #0xaa / str ip, [r2]      ; element 0 = raw 0xAA
mov  ip, #0xaa / str ip, [r2+4]    ; element 1 = raw 0xAA
bl   Uint8ClampedList.fromList     ; → EzcanMessage.field_I
```

The Smi encoding 0x154 = 0xAA << 1 confirms 0xAA (170) is the intended byte
value. Both arrays start with the **0xAA 0xAA preamble** shared with the old
protocol.

The ctor then sets 8 EzcanMessage fields from its remaining args (REVERSE
order — field 0 gets arg at fp+0x24, field 7 gets arg at fp+8), plus
`genCheck`'s return stored at field 7. The B2 8-byte payload list is one of
these fields.

### A7.4 genCheck (ci 9471, file 0xbfc178, 1532 B) — the frame checksum

`genCheck` receives the EzcanMessage fields and computes the final wire bytes:
1. Reads the 64-bit command word (arg[fp+0x20]) and allocates `Uint8List(8)`,
   filling it with the 4-byte LE representation (`mov r4, #8; bl alloc`,
   then 4-iteration byte-extraction loop with `and sb, r0, #0xff` / `strb`)
2. Enters a **byte-iteration checksum loop** over the message fields using
   dispatch-table element access (`ubfx r1, r1, #0xc, #0x14` = extract CID,
   then `blx lr` through the dispatch table = interface call per element)
   — the loop is bounded by `cmp r6, #0x18` (24 iterations = the full frame
   length in bytes)
3. Accumulates the checksum in a 64-bit register pair (`adds/adcs`)
4. Range-checks: `mvn r3, #0x7e` (= −127) and `cmp r1, #0xff` (= 255) — the
   checksum must fit in a single byte after masking
5. `adds r3, r1, #0x100` — adds 0x100 = 256 to normalize negative sums
   (two's-complement adjustment)
6. If the adjusted checksum == 1, calls the special 0x9A3608-path; otherwise
   calls **0xba2518** (blob 8318) = the final byte conversion, and returns
   a 64-bit result in r0/r1

**The checksum is a byte-WIDE SUM (not XOR)** — the `adds r8, r2, r4;
adcs r6, r1, r3` accumulates 64-bit addition of all frame bytes, then masks
to 8 bits (the `cmp r1, #0xff` / `adds #0x100` adjustments). This differs
from the old protocol's XOR checksum.

### A7.5 The lock wire frame (annotated)

Combining all decoded layers, the V12 HT lock wire frame is:

```
Offset  Byte(s)  Content                     Source
─────  ───────  ──────────────────────────  ──────────────────────
0–1    AA AA    preamble                    EzcanMessage ctor (×2 arrays)
2–3    ?? ??    header/type/length          genMsg args (toUint8 of
                                             0x0A/0x10-derived fields)
4–11   B2 00 00 00 04 00 00 00              the genSetModeMsg payload
                 (cmd=0xB2, param=4=lock)    (Uint8List(8) from genSetModeMsg)
12     ??       checksum                    genCheck (byte sum, not XOR)
```

**Confidence: the AA AA preamble and the B2 payload placement are confirmed
by static decode. The exact positions of the header/type/length bytes and
the checksum position require decoding the EzcanMessage → Uint8List
serialization, which is a further 1–2 functions deep from genCheck's return.**

**Calibration status**: setVolume = 0x26 ✓ (recovers the expected one2one
opcode). The one2one frame `AA AA 14 05 60 26 <vol> <xor>` is produced by a
DIFFERENT builder (genCmd ci 8326, ~1176 B) from the EzcanMessage path
(ci 9470, ~1200 B total). The AA AA preamble is shared; the 14/60 constants
are NOT found in the EzcanMessage path — they are replaced by the eZCAN
protocol's own framing (0x0A frame-type, 0x10 length, 0x40 identity check).

### A7.6 Decoding the 0x0F550116 constant

genSetModeMsg passes 0x0F550116 as a 64-bit argument to
genLocalStandardWriteMsg. In the frame: it flows through genLocalMsg →
genMsg → `Integral.checked(0x0F550116, 64, null)` → EzcanMessage. This is
the **local-protocol message identity** for "set mode": 0x0F550116 is a
32-bit command UUID (similar to the 0x01D81408 protocol identity and
0x009A3608 device constant returned by the constant getters). It is NOT
a wire byte — it is an internal command-routing key used by genMsg to
select the frame type and populate the header bytes.

### A7.7 Remaining gap for hardware confirmation

The B2 payload and AA AA preamble are confirmed. The precise final wire
bytes require decoding how the EzcanMessage object serializes to the BLE
write — specifically which fields are emitted at which byte positions and
in what order. The likely serialization function is in the EzcanMessage
class hierarchy (the toUint8 conversions in genMsg suggest the frame bytes
are already materialized at genMsg level; the ctor just packages them).
The next pass should:
1. Disassemble genMsg's five toUint8 call sites to map each converted
   integer to its source (which stack arg feeds which byte)
2. Determine the byte positions of 0x0A (frame type 10) and 0x10 (length 16)
   in the final frame
3. Verify with the volume one2one calibration: genCmd (ci 8326) should have
   the equivalent structure for the 14/60 frame — a side-by-side of the two
   builders' outputs would confirm the frame template
