# INMOTION 8.1.34 `libapp.so` — V12 protocol RE, pass 2 notes

Date: 2026-10-30. Status: **container format mapped, wire-format opcodes NOT recovered.**
This pass was a bounded best-effort attempt to extract V12 command constants that WheelLog
does not cover (BMS cells request, speed-limit sets, power off / reboot, calibration, RTC
sync, authenticated writes). No opcode below is presented as a usable constant — everything
unresolved is labeled as such.

Target: `C:\Users\Bishkeks\Documents\inmotion-research\input\8.1.34\libapp.so`
SHA-256 `92a24d380f129750f4893e92e823b878db531dee5483936b1d66c07d015263f6`, 23,462,480 bytes.

---

## 1. Tooling findings (why native RE tools were not used)

| Tool | Result |
| --- | --- |
| REA `open_binary` | Opens the file fine (ELF, ARM), but **no deep provider can analyze it on this host**: Ghidra P0 on Windows accepts native PE / raw 6502 only (`target_format_unsupported` for ELF); Hopper unsupported on win32; IDA and Kuna not configured. |
| REA `execute_rizin_command` | Fails with `ERR_REA_WINDOWS_NATIVE` (Rizin native execution unavailable on this Windows host). |
| REA `inspect_dart_aot` | APK-oriented (string-pool projection only); adds nothing beyond the string offsets already in `evidence/`. |

Consequence: all work below was done with direct byte parsing (Python 3.11, `-I` mode,
scripts in `%LOCALAPPDATA%\Temp\inmotion-re\`, nothing written into the repo or `Desktop\ion`
except this file).

## 2. Observed facts about the target (confidence: observed)

- **ELF32, ARM (EM_ARM, EABI), not ARM64.** This is the `armeabi-v7a` ABI split of the XAPK
  (`e_ident[4]=1`, `e_machine=0x28`). The task brief said ARM64 — that is incorrect for this file.
- Sections: `.rodata` `0x140–0x957838` (snapshot data), `.text` `0x958000–0x165C378`
  (Dart AOT instructions, ARM mode — e.g. `push {r11,lr}` = `e92d4800` at `0x958054`).
  **Program-header file offsets == virtual addresses (identity mapping).**
- Dynsym exports the four standard snapshot symbols:
  `_kDartVmSnapshotInstructions` `0x958000` (19,936 B), `_kDartIsolateSnapshotInstructions`
  `0x95ce00` (13,628,792 B), `_kDartVmSnapshotData` `0x140` (24,880 B),
  `_kDartIsolateSnapshotData` `0x6280` (9,770,424 B), `_kDartSnapshotBuildId` `0x114`.
- Snapshot version hash `adb4292f3ec25074ca70abcd2d5c7251` (matches the row range listed in
  darter's `info/versions.md`, Dart 3.7.x era). Features string:
  `product no-code_comments no-dwarf_stack_traces_mode no-lazy_dispatchers dedup_instructions no-asserts arm android no-compressed-pointers null-safety`
  → `no-dwarf_stack_traces_mode` means **function names are retained** in the snapshot;
  `no-compressed-pointers` (32-bit raw pointers) as expected for ARM32.
- Header (both blobs), little-endian: `magic u32 = 0xdcdcf5f5`, `length i64`, `kind i64 = 3`
  (kFullAOT). VM blob: length 3522, 6 clusters, 1087 objects. Isolate blob: length
  `0x337941`, 1055 clusters, 317,129 objects, instructions-table length **46,112**,
  rodata offset 64. Stream (cluster data) is followed by a **read-only data image**
  containing the actual string/RO objects (image starts at isolate-blob offset `4+length`
  = `0x337945`).
- Dart object headers in the image (32-bit tags layout, verified against many samples):
  `tags u32` (bits 0–7 flag bits, bits 8–11 size-in-8-byte-units, **bits 12–31 cid**),
  `hash u32`, then payload. OneByteString: `[tags][hash][length Smi][bytes]`.
  Verified: `0x467858` = cid 93, len 14, `"handleLostMode"`; `_V12CmdV1@3638477340` object
  at `0x6b3ed8` (tags `0x0005d432`), data at `0x6b3ee4` — **OneByteString cid = 93 in this
  build**, confirming the earlier evidence note "CID 93".
- Integer stream encodings (verified against Dart SDK sources 3.7.0/3.7.12/3.8/3.9/3.10/main):
  - `ReadUnsigned` / `WriteUnsigned`: LEB-style, little-endian 7-bit groups, **terminator
    byte has the high bit set and contributes `(byte − 0x80) << shift`**.
  - `Read<T>`/`Write<T>` (signed, e.g. the per-cluster `tags u32`): same but terminator
    subtracts `0xC0`.
  - Ref ids: `ReadRefId`/`WriteRefId` — **big-endian** 7-bit groups, terminator byte is the
    one with the high bit set (signed byte < 0), result **+128**.
- cid numbering (3.7-era `class_id.h`, cross-checked): Object=4, Class=5, Function=6,
  Code=17, Instructions=19, ObjectPool=22, Smi=59, Mint=60, String=91, OneByteString=**93**
  (empirically confirmed), TwoByteString=94±1.
- Cluster framing per SDK sources (all checked versions): per cluster
  `[tags (signed-LEB u32)][WriteAlloc][… later WriteFill]`; clusters are written
  canonical-strings-first (OneByteString, TwoByteString, String, Mint, Double, TypeParameter,
  Type, TypeArguments), then Code, Function, Closure, then the rest by cid. ROData/string
  clusters have **no fill in the stream** (objects live in the RO image); their alloc section
  is `[count][per-object image-offset delta >>3][canonical-set layout]`
  (`app_snapshot.cc`, `RODataSerializationCluster::WriteAlloc`).
- ObjectPool cluster (the target for opcode extraction):
  alloc `[count][length per pool]`; fill per pool `[length][entries…]` where each entry is
  `[entry_bits u8]` + tagged ref (ReadRefId) or immediate (signed-LEB intptr)
  (`ObjectPoolDeserializationCluster::ReadFill`, 3.7.0 lines 3235–3320).
- Smi constants are **not** pool immediates: there is a dedicated cluster that serializes all
  Smi/Mint constants as objects with their own ref ids (3.7.0 `app_snapshot.cc` ~line 5306).
  This invalidates naive "find the byte in a pool" scans; opcodes must be recovered via the
  ref-id graph (Function → Code → ObjectPool → Smi ref).

## 3. Where the full parse is blocked (the one remaining format unknown)

- darter (`github.com/mildsunrise/darter`, cloned to `%TEMP%\darter`) is a Dart-2.x-era parser;
  its version hash warning is benign, but it fails on this snapshot for concrete reasons:
  1. it reads only **4** header counts; this snapshot has **5**
     (`num_base_objects, num_objects, num_clusters, instructions_table_len, instructions_table_rodata_offset`) — patched locally;
  2. its Kind table lacks `kFullCore` (kind 3 = kFullAOT here) — patched locally;
  3. **cluster `tags` encoding mismatch (unresolved blocker).** The first isolate-stream
     cluster starts at isolate-blob offset **212** with signed-LEB value **185**
     (bytes `39 c1`). 185 decodes exactly as `(92 << 1) | 1` — i.e. cid 92 with a canonical
     bit — which is *plausible* (first cluster must be the canonical String/OneByteString
     ROData cluster; the following unsigned-LEB count reads 89855 candidate strings), but it
     contradicts the published `ClassIdTag::encode` (cid at bits 12–31) in every SDK source
     checked (3.7.0, 3.7.12-fetch failed, 3.8.0, 3.9.0, 3.10.0, main). Verification attempts
     of the subsequent `[count][89855 offset-deltas]` interpretation against the RO image
     (base `0x33DBC5`, delta<<3 accumulation) produced misaligned/non-object addresses
     (e.g. first accumulated address `0x46783D`, unaligned), so either an extra header
     section exists between the counts and the clusters in this build, or the tags/delta
     encoding differs from all fetched sources. Residual unknown — do not guess.
- Net effect: ref ids cannot yet be assigned, so Function→name and Code→ObjectPool cannot be
  resolved statically yet.

## 4. Wire-format findings for V12 commands

**No new opcode was confirmed. Per the no-fabrication rule, nothing below should be used
on hardware.** Everything already known from WheelLog (flags 0x14 / cmd 0x60, the 23 covered
sub-commands, power-off via Initial 0x11 + cmd 0x03 `81 00` then `82`) is unchanged.

Searches performed and why their outputs are not evidence:

1. **ARM immediate scan of `.text`** (patterns `E3A0 X0NN` mov / `E300 X0NN` movw, 13,035
   sites): best 16 KB window contains only 8/23 covered opcodes — the V12 factory functions
   are neither adjacent nor immediate-loading; command bytes are pool constants. Dead end.
2. **Pool-entry Smi pattern scan** (`[entry_bits][WriteRefId(smi<<1)]`): invalidated by the
   SDK source (Smis are separate cluster objects); produced 10³–10⁴ false positives/op.
3. **Signed-LEB two-byte constant pairs** `[v, 0xC0]` for v ∈ {0x40,0x42,0x43,0x50,0x51,0x60,0x3f}
   with 400-byte proximity grouping: ~50 groups found (e.g. isolate-stream `0x6b519–0x6b5cf`
   containing 0x40/0x43/0x50/0x51; `0x233a50–0x234440` containing five of them), but these
   byte pairs occur generically in any LEB stream and could not be tied to pool structures.
   **Unverified noise — recorded only as leads.**
4. Raw `E3A0`/literal-pool dword scans for Smi 0x28 (flags 0x14) adjacent to Smi 0xC0
   (cmd 0x60) in `.text` and `.rodata`: zero hits (consistent with constants living in the
   serialized Smi cluster, not as raw dwords).

## 5. Authentication gating of V12 writes

**Not determinable from this pass.** What is established:

- Present in the snapshot (string data offsets from `evidence/protocol-candidates.json`):
  `genVerifyPwdMsg` `0x5c52cc`, `genVerifyCryptKeyMsg` `0x627294`, `genRequestEncryptedKeyMsg`
  `0x4adddc`, `genSetPasswordMsg` `0x7940dc`, `genRequestPasswordTypeMsg` `0x597374`,
  `genSetPasswordTypeMsg` `0x597dac`, `authenticated_signed_writes` `0x706b54`,
  `genLocalStandardWriteMsg` `0x53ea64`, `genSpecialOne2OneWriteMsg` `0x63aedc`,
  plus `elephant/cryptor.dart` `0x48c90c`, `util/aes.dart` `0x4b9b0c`, and
  `V12Settings{passwordType: ` `0x7a3214` (V12 settings carry a password-type field).
- WheelLog's covered commands are sent with **no** authentication exchange, so at minimum the
  WheelLog-covered command set works without a key exchange on the wheels it supports.
- Whether the official app gates *other* writes (calibration, power, limits) behind
  VerifyPwd/EncryptedKey on V12 HT **remains unknown**; the names above only prove the
  machinery exists somewhere in the protocol stack (multiple model families share it).

## 6. Recommended next pass (concrete, ranked)

1. **Finish the darter port** (the deltas are now precisely known, see §2–§3):
   5 header counts; kind table + kFullCore; resolve the cluster-tags encoding
   (candidates: `(cid<<1)|canonical`, an extra pre-cluster section, or a changed
   `ClassIdTag` position in this exact SDK build — obtain the *exact* Dart SDK version by
   matching the snapshot hash against SDK release tags rather than darter's table);
   implement `CanonicalSetSerializationCluster` alloc (offset deltas + canonical-set layout),
   `ReadRefId` (+128 bias, big-endian), the Smi/Mint cluster, Function AOT fill
   (`[from-to refs][code_index uleb][kind_tag]`), Code and ObjectPool fills.
   Then: for each `gen*Msg` Function → Code → ObjectPool → Smi refs → opcode bytes, and
   Function.name for the mapping. This is a mechanical, bounded job once tags decode.
2. Alternative tooling: **blutter** (worawit/blutter) — builds the exact matching Dart SDK
   and fully parses this hash family; heavy (needs Dart SDK source + build) but proven.
3. Cheap partial win even without names: once the Smi/Mint cluster fill is located, *all*
   integer constants of the app (including unknown V12 opcodes) appear with sequential
   ref ids; cross-referencing ref ids against the pool region anchored by the distinctive
   in-function literal `"Low beam and high beam state should be set in the same time in V12."`
   (string data `0x4cca74`) would attach opcodes to functions without full graph parsing.
4. Dynamic confirmation (out of scope here): Frida hook on the app's BLE write path while
   exercising each settings screen remains the ground truth for any constant extracted.

## 7. Reproducibility artifacts (outside the repos)

- Scripts: `%LOCALAPPDATA%\Temp\inmotion-re\` (`elf.py`, `probe1–10.py`, `dartparse.py`).
- Patched darter clone: `%TEMP%\darter` (kind table + 5-count header patched; cluster
  parsing still fails at the tags unknown above). Deps installed: `pyelftools`, `capstone`.
- No file under `Desktop\ion` other than this note was created or modified; everything under
  `Documents\inmotion-research` was read-only.
