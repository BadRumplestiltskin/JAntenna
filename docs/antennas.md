# jvoacap Antenna Pattern Architecture

**Status:** Design specification — locked 2026-05-17
**Authors:** jvoacap maintainers
**Implements:** Unified `GainTable` internal representation, filesystem-backed antenna repository, manual bake CLI
**Implementation tracking:** TD-105 (TYPE-13 parser, landed `7c80801`) and follow-on TDs as phases land

---

## 1. Purpose and scope

This document specifies how jvoacap stores, loads, and evaluates antenna patterns at runtime. It replaces the ad-hoc mix of `AntennaModel`, `AntennaModel2D`, `*GainCalculator` analytical classes, and `VoaAntennaReader` dispatching that exists today with a single internal representation (`GainTable`) and a filesystem-backed pattern repository populated by a manual bake CLI.

**In scope:**

- The runtime data shape for antenna gain patterns.
- The on-disk format for the repository (`.gtable` binary + `.json` metadata sidecar).
- The bake CLI (`jvoacap-antennas`) that ingests source antenna files (voacapl `.voa`, ITU-R Rec.705 `.t13`, Cebik `.13`) and HFMUFES/CCIR analytical-formula parameters into the repository.
- The runtime path from a deck `ANTENNA` card through repository lookup to per-mode gain values.
- The migration plan from the current state to the locked design.

**Out of scope:**

- The catalogue of voacapl analytical formulas themselves (covered by `mufesgan.for`, `iongain.for`, `antinit2.for` and the existing `*GainCalculator.java` classes).
- The physics of the link budget — antennas feed `TGAIN`/`RGAIN` into `ModeEnumeratorService`; what happens after that is unchanged.
- Multi-user / multi-tenant deployment. jvoacap is a single-user research tool; the design assumes single-process, single-filesystem.

---

## 2. Why this design exists

Before this redesign, jvoacap's antenna handling had four problems:

1. **Multiple representations.** `AntennaModel` (91 elevation gains), `AntennaModel2D` (360 × 91 az × el), and the 17 `*GainCalculator` analytical classes all coexisted with different APIs. The `ModeEnumeratorService` had to dispatch between them.
2. **Inconsistent type numbering.** voacapl uses three overlapping numbering schemes (CCIR jant 0–12 + 13/14 for tables, IONCAP jant 21–30, HFMUFES jant 31–47). jvoacap implements only HFMUFES KOP 1–17, conflating "voacapl Type 13 .voa file" (CCIR Area gain table) with "jvoacap KOP 13" (HFMUFES Horizontal Log-Periodic). These are different antennas with the same number.
3. **No support for ITU-R Rec.705 multi-frequency catalogues.** 532 files in `/Users/warrensly/NetBeansProjects/ITU-R-HF/.../T13 Files/` use FORTRAN `(9x,10f7.3)` fixed-format storage; the existing parser handled only the trailing-whitespace variant.
4. **No persistent, version-fingerprinted artifacts.** Every prediction re-evaluated analytical formulas at runtime; pattern files were re-parsed per card. No way to detect that a `.voa` file had changed between predictions.

The unified design solves these by:

- Treating every antenna — analytical or pattern-file — as a baked `GainTable`.
- Persisting `GainTable`s as immutable artifacts in a filesystem repository.
- Requiring explicit ingestion via a CLI (no runtime baking, no implicit fallback).
- Fingerprinting source files so staleness is detectable at startup.

---

## 3. Internal representation: `GainTable`

### 3.1 Shape

```
record GainTable(
    double[] frequenciesMHz,    // F entries (F >= 1), sorted ascending
    double[] azimuthsDeg,       // A entries (A >= 1), sorted ascending, in [0, 360)
    double[] elevationsDeg,     // E entries (E >= 1), sorted ascending, in [0, 90]
    short[]  gainsCentiDb       // flat F*A*E, row-major; gain_dBi = value / 100.0
                                // sentinel Short.MIN_VALUE = "below horizon"
)
```

**One internal type covers every voacapl antenna kind:**

| Source kind | F | A | E | Notes |
|---|---:|---:|---:|---|
| Isotrope (Type 0) | 1 | 1 | 1 | One number, broadcast everywhere |
| 91-elevation table (Type 11) | 1 | 1 | 91 | Elevation pattern, freq- and az-independent |
| 360 × 91 az × el table (Type 13) | 1 | 360 | 91 | Single-frequency directional pattern |
| 30 × 91 freq × el table (Type 14) | 30 | 1 | 91 | Frequency-dependent, no azimuth |
| Multi-frequency Type-13 (ITU-R Rec.705 catalogue) | F (per source) | 360 | 91 | Stacks single-freq files |
| HFMUFES analytical (KOP 1–17), baked | 28 | 360 | 91 | Pre-computed at 3–30 MHz × 1° × 1° |
| CCIR analytical (jant 1–10, 12), baked | 28 | 360 | 91 | Same grid as HFMUFES |

### 3.2 Why fixed-point int16 × 100

| Encoding | Bytes/sample | Resolution | Total for ITU-R full (28×360×91) | File-parity preserved? |
|---|---:|---|---:|---|
| `double` | 8 | exact | 7.34 MB | yes |
| `float` | 4 | ~7 decimals | 3.67 MB | yes |
| **`short` × 100 (locked)** | **2** | **0.01 dB** | **1.83 MB** | **yes** (voacapl F7.3 file resolution = 0.001 → quantised to 0.01) |
| `byte` × 4 | 1 | 0.25 dB | 0.92 MB | no (below parity-test tolerance ±0.05) |

`short` × 100 covers the practical antenna gain range (±327.68 dB) at 0.01 dB resolution, well below the 4.60 dB closure-ladder mean and the ±0.05 dB parity-test tolerance from TD-63 (`VoaPatternParityTest`). The precision loss vs `double` is dominated by every other error source in the HF prediction chain. Conversion to `double` at lookup time is one integer multiply, sub-nanosecond on modern CPUs.

### 3.3 Sentinel encoding

`Short.MIN_VALUE` (-32768) is reserved as the "below horizon" sentinel. It encodes `-99.99` / `-99.999` from voacapl-format pattern files (Cebik / ITU-R), preserved across the value-to-int16 conversion. Any real gain value at the elevation = 0 row maps to this sentinel.

Distinct from any representable gain value (e.g., a true `-99.99 dB` gain entry would map to `-9999`, not `Short.MIN_VALUE`).

### 3.4 Flat array layout

The gains are stored as a single flat `short[F*A*E]` in row-major order (`flat[f*A*E + a*E + e]`), not as `short[F][A][E]`. Reasons:

- One JVM allocation, one object header (~16 bytes) instead of `F + F*A + 1` headers.
- Cache-friendly: contiguous memory enables hardware prefetch.
- For full ITU-R: 1.83 MB single allocation vs ~2.0 MB array-of-arrays-of-arrays.

The two `int` strides `[A*E, E]` are derived at construction, not stored.

### 3.5 Axis semantics

| Axis | Boundary behaviour | Singleton handling |
|---|---|---|
| `frequenciesMHz` | Out-of-range queries are clamped to nearest endpoint (no extrapolation). Linear interpolation between bracketing values. | F = 1: lookup ignores frequency axis. |
| `azimuthsDeg` | Wrap-around: queries are reduced modulo 360 before bracketing. The "last cell" wraps to the first. | A = 1: lookup ignores azimuth axis. |
| `elevationsDeg` | Clamped to `[0, 90]`. Linear interpolation between bracketing values. | E = 1: lookup ignores elevation axis. |

Decision: linear-in-MHz frequency interpolation matches voacapl's `gainterp.for` for parity.

### 3.6 Lookup: `gainDbi(freqMHz, azDeg, elDeg)`

Trilinear interpolation on the regular grid:

1. For each axis, find the bracket `[lo, hi]` and weight `w` (clamp or wrap per §3.5).
2. Sample 2³ = 8 corners of the tensor at the bracket indices.
3. **Sentinel handling** (locked S13): if any corner with `el = elLo` is the sentinel value, collapse the elevation axis by using the `el = elHi` corner values and setting `wE = 0`. This prevents catastrophic interpolation across the horizon discontinuity (a query at `el = 0.3°` between the sentinel at `el = 0` and a real value at `el = 1` would otherwise produce ~-53 dB instead of the physically-sensible value near -7 dB).
4. Trilinear weighted sum of the (possibly collapsed) corners. Return result.

If the query is exactly at `el = 0°`, the result is the sentinel itself (returns `-99.99`). Downstream code in `ModeEnumeratorService` treats a sentinel-valued gain as "mode unphysical" and excludes it from the link budget.

### 3.7 Out-of-range query behaviour

Locked S12: log-warn and clamp to nearest endpoint. Default behaviour for all queries outside the axis range. An opt-in strict mode (`-Djvoacap.antennas.strict=true`) instead throws.

### 3.8 Factory methods

The same `GainTable` is produced by per-source factory methods:

```
GainTable.fromIsotrope(double maxGainDbi, double designFreqMHz)
GainTable.fromType11Elevations(double[] elevGains91, double maxGainDbi, double designFreqMHz)
GainTable.fromType13AzEl(double[][] gains360x91, double designFreqMHz)
GainTable.fromType14FreqEl(double[][] gains30x91, double[] frequencies)
GainTable.fromMultiFreqType13(NavigableMap<Double, double[][]> byMHz)
GainTable.fromAnalyticalBake(BakerFunction baker, int F, int A, int E, double[] freqs)
```

The factory methods handle the absolute-vs-offset-from-max convention per source (see §7.3) and the sentinel preservation. Downstream code never sees the raw source-file format.

### 3.9 Memory characteristics

| Pattern | Tensor shape | Tensor bytes | Total (incl. axes + headers) |
|---|---|---:|---:|
| Isotrope | 1×1×1 | 2 | ~120 B |
| Type 11 | 1×1×91 | 182 | ~900 B |
| Type 13 single freq | 1×360×91 | 65,520 | ~68 KB |
| Type 14 | 30×1×91 | 5,460 | ~6 KB |
| ITU-R 141 multi-freq (28 MHz) | 28×360×91 | 1,834,560 | ~1.83 MB |
| HFMUFES analytical baked at standard grid | 28×360×91 | 1,834,560 | ~1.83 MB |

Per-card memory (TX + RX antennas): at most ~3.7 MB worst case. Per-process for a session with 100 distinct antennas loaded: ~183 MB. Fits any modern JVM heap.

---

## 4. The repository

### 4.1 Layered model

Locked B2 = layered.

```
+--------------------------------------------------+
| User layer (overrides + extensions)              |
| Location: $jvoacap.antennas.path                 |
|           default: ./antennas/                   |
| Writeable by the bake CLI                        |
+--------------------------------------------------+
                       |
                       v searches user first
+--------------------------------------------------+
| Defaults layer (immutable, shipped with jar)     |
| Location: classpath:antennas/                    |
| Contains: SWWHIP, const5, const17 (voacapl 3)    |
+--------------------------------------------------+
```

Resolution rule: look in the user layer first; fall back to defaults. A `.gtable` in the user layer with the same name as a default overrides the default.

### 4.2 Default catalogue (shipped in the jar)

Locked S5: the day-1 default catalogue contains the three voacapl-shipped antennas:

| Logical name | File | Source | Notes |
|---|---|---|---|
| `default/SWWHIP` | `SWWHIP.gtable` + `.json` | voacapl `swwhip.voa` (Type 11) | Receive whip, ~0 dBi peak |
| `default/const5` | `const5.gtable` + `.json` | voacapl `const5.voa` (Type 11) | Constant 5 dBi reference |
| `default/const17` | `const17.gtable` + `.json` | voacapl `const17.voa` (Type 11) | Const 17 dBi VOA transmit |

Plus the implicit Type-0 isotrope which doesn't need a file (callers can request `isotrope` and get a synthetic `GainTable.fromIsotrope(0.0, freq)`).

Anything else — Cebik antennas, ITU-R Rec.705 catalogue, HFMUFES analytical bakes, user designs — must be baked into the user layer by the bake CLI.

### 4.3 User layer path

Locked: `-Djvoacap.antennas.path=...` system property, default `./antennas/` (working-directory-relative).

The default matches the voacapl convention of expecting antenna files at a known relative path (`~/itshfbc/antennas/`) but uses CWD instead of `$HOME` for jvoacap-locality. Users running jvoacap from arbitrary directories can override with `-D` once per shell session.

If the user-layer directory doesn't exist, the runtime treats it as empty (defaults layer is still loaded). The bake CLI creates the directory on first write.

### 4.4 Directory structure

Locked S8: hierarchical, mirroring voacapl's `itshfbc/antennas/<group>/<name>`.

```
antennas/
  default/                           <- shipped defaults (in jar, copied to user repo on convenience)
    SWWHIP.gtable
    SWWHIP.json
    const5.gtable
    const5.json
    const17.gtable
    const17.json
  itur705/                           <- user-baked ITU-R Rec.705 catalogue
    141-multifreq.gtable             <- 28-freq multi-freq Type-13 stack
    141-multifreq.json
    146-multifreq.gtable
    146-multifreq.json
    ...
  cebik/                             <- user-baked Cebik catalogue
    sbrr216a.gtable
    sbrr216a.json
    ...
  hfmufes/                           <- user-baked HFMUFES analytical
    yagi-3el-default.gtable
    yagi-3el-default.json
    ...
  ccir/                              <- user-baked CCIR analytical
    horiz-lp-default.gtable
    horiz-lp-default.json
  user/                              <- user designs
    my-array-rev3.gtable
    my-array-rev3.json
  manifest.json                      <- optional catalogue summary, regenerated by bake CLI
  .lock                              <- bake-tool concurrency lock (created during bake, removed on exit)
```

### 4.5 Pattern naming

Each pattern is a (group, name) pair. The group is a subdirectory (`default`, `itur705`, `cebik`, `hfmufes`, `ccir`, `user`, or any user-defined name). The name is the file basename without extension.

Cards reference patterns by the bracketed path syntax (see §6.2). Two equivalent forms are accepted:

- `[default/SWWHIP.VOA]` — voacapl-compatible literal; extension stripped, resolves to `default/SWWHIP`.
- `[default/SWWHIP]` — direct logical reference.

Locked S7: both forms work; voacapl decks remain compatible without rewriting.

---

## 5. The bake CLI: `jvoacap-antennas`

### 5.1 Packaging

Single jar with sub-command routing, exposed as a shell wrapper (`bin/jvoacap-antennas`) that forwards to `java -jar jvoacap.jar antennas "$@"`. See §11.

### 5.2 Sub-commands

| Sub-command | Synopsis |
|---|---|
| `bake <source-file>` | Bake one source file into the user-layer repo. Auto-detects format by extension and header. |
| `bake-dir <dir>` | Recursively bake every `.voa`/`.13`/`.t13` file in `dir`. |
| `bake-analytical <kop> <params-json>` | Bake an analytical antenna (HFMUFES KOP 1–17 or CCIR jant 1–10, 12) at the given parameters. |
| `verify` | Walk the repository, recompute source SHA-256s, report any drift (exits 0 = clean, 2 = drift detected). |
| `list [--group <name>]` | List patterns in the repository, optionally filtered by group. |
| `show <pattern-name>` | Dump metadata + sample gain values for one pattern. |
| `remove <pattern-name>` | Delete a pattern's `.gtable` + `.json` from the user layer. |

### 5.3 Bake invariants

1. **Source SHA-256 captured.** Computed from the input file (or from canonical JSON of analytical parameters) and stored in the metadata sidecar.
2. **Atomic write.** The bake writes to `name.gtable.tmp` and `name.json.tmp`, then renames both atomically (`Files.move` with `ATOMIC_MOVE`). Failures mid-write leave no partial file.
3. **Lock-file concurrency.** Before any write, the CLI takes an exclusive lock on `<repo>/.lock`. Second concurrent invocation fails fast with a clear error.
4. **Manifest regeneration.** After a successful bake, the CLI rewrites `<repo>/manifest.json` summarising every pattern.
5. **Provenance recorded.** Every bake stores `baker_version` (the SHA of the baker module at compile time), `jvoacap_version` (the running release), and `baked_at` (UTC timestamp).

### 5.4 Drift detection (`verify` sub-command)

For each pattern in the user layer with a non-null `source.path` in its metadata:

1. If the source file no longer exists at the recorded path → report `source missing`.
2. Recompute SHA-256 of the source file; compare against `source.sha256` in metadata. Mismatch → report `source changed`.
3. Compare metadata `baker.sha` against the running build's baker SHA. Mismatch → report `baker changed` (indicates the formula or pattern parser has been modified since this pattern was baked).

`verify` exits 0 on clean, 2 on any drift detected, with a summary dump of which patterns and which kind of drift.

### 5.5 Startup drift check

Locked B3 = warn-and-continue. On jvoacap startup (server or CLI), the runtime walks the repository, runs the same checks as `verify`, and logs a WARN banner per stale pattern. It does not refuse to start. The stale baked pattern continues to be used.

Opt-in strict mode (`-Djvoacap.antennas.strict=true`) instead refuses to start on any drift.

### 5.6 No automatic baking

Locked: no runtime baking, no auto-cache, no on-demand bake-and-cache fallback. If a card references an antenna not present in the repository, the runtime throws `AntennaNotInRepository` with a message instructing the user to run `jvoacap-antennas bake <source>`.

Tradeoff accepted: research-workflow friction (drop file, run CLI, then run prediction) in exchange for explicit reproducibility and no silent-staleness paths.

---

## 6. Runtime path

### 6.1 Card load

When the deck parser encounters an `ANTENNA` card, it extracts:

- The terminal (`TX` or `RX`).
- The bracketed pattern reference (e.g., `[default/SWWHIP.VOA]`).
- The beam direction (`beam_main`), power, and frequency range.

The pattern reference is normalised:

1. Strip any extension (`.VOA`, `.voa`, `.13`, `.t13`).
2. The result is a (group, name) pair: `default/SWWHIP`.

### 6.2 Repository lookup

The runtime queries the layered repository in order:

1. User layer at `$jvoacap.antennas.path` (default `./antennas/`).
2. Defaults layer at `classpath:antennas/`.

The first hit wins. The `.gtable` is parsed into a `GainTable` (one disk read per antenna per JVM lifetime; the result is cached). The `.json` metadata is loaded and held alongside for diagnostics.

If neither layer contains the pattern, the runtime throws `AntennaNotInRepository(group + "/" + name)` with the message "Pattern not in repository. Run: jvoacap-antennas bake <source>".

### 6.3 Per-mode evaluation

For each propagation mode at each frequency, `ModeEnumeratorService` invokes:

```
double tGain = txAntenna.gainDbi(freqMHz, txOffsetAzDeg, takeoffElevDeg);
double rGain = rxAntenna.gainDbi(freqMHz, rxOffsetAzDeg, arrivalElevDeg);
```

`txOffsetAzDeg` and `rxOffsetAzDeg` are the off-beam azimuths (path bearing relative to the antenna's main beam direction from the `ANTENNA` card). These are unchanged from today.

The lookup is trilinear interpolation on the `GainTable`'s flat `short[]`. No dispatcher, no per-mode formula evaluation, no allocation in the hot path.

#### 6.3.1 Strict mode (TD-110c, opt-in)

Set `-Djvoacap.antennas.strict=true` (default: `false`) to disable the runtime fall-back tiers. When strict mode is on:

- **Tier 1 (GainTable from repository)** — used when the card's antenna reference resolves through `GainTableRepository`. Same as non-strict mode.
- **Tier 2 (legacy `.voa` file load)** — *disabled*. If the repository doesn't have the pattern, `loadVoaPatternIfPresent()` throws `AntennaNotInRepository` with a message pointing the user at `jvoacap-antennas bake <source>`.
- **Tier 3 (analytical `ANT_TYPE` dispatch)** — *disabled*. Cards built with `ANT_TYPE` alone (no `.voa` reference) throw `AntennaNotInRepository` at first mode evaluation, naming the analytical KOP that needs to be baked.

Non-strict mode (default) preserves the three-tier behaviour from Phase C — useful during the migration window when many test cards still rely on the analytical path. Strict mode is the eventual production state once the canonical default catalogue + legacy-`antType` lookup table are fully populated (deferred to a later TD).

Strict-mode behaviour is exercised in CI by `Phase_D_StrictModeTest`, which `@TestPropertySource`-flips the flag and verifies (a) Tier 1 hits work, (b) unknown patterns throw, (c) analytical-only cards throw with a clear error.

### 6.4 Backward compatibility — JSON `/run` API

Locked B4: deprecate `antType` in JSON requests; require `antennaName`.

Today:

```
POST /api/voacap/run
{
  "frequencyMHz": 14.15,
  "antType": 3,
  "antHeight": 0.1,
  ...
}
```

After migration:

```
POST /api/voacap/run
{
  "frequencyMHz": 14.15,
  "antennaName": "default/SWWHIP",
  ...
}
```

During a one-release grace period, requests carrying the old `antType` field are accepted with a deprecation warning logged, and resolved via a lookup table that maps common `(antType, antHeight)` tuples to a canonical pre-baked pattern (e.g., `antType=3, antHeight=0.1` → `default/dipole-0.1wl`). After the grace period, the lookup table is removed and the request is rejected with a clear error.

---

## 7. `.gtable` binary file format

### 7.1 Byte layout

Little-endian. Magic + version + 3 axis counts + 3 axis value arrays + flat gain array.

| Offset | Size | Field | Type | Notes |
|---:|---:|---|---|---|
| 0 | 8 | magic | `byte[8]` | ASCII `"JVOAGT01"` (J-V-O-A-G-T-0-1) |
| 8 | 4 | version | `int32` LE | Current value: 1 |
| 12 | 4 | flags | `int32` LE | Reserved for future flags (compression, sparse encoding). Current value: 0 |
| 16 | 4 | F | `int32` LE | Frequency axis length, ≥ 1 |
| 20 | 4 | A | `int32` LE | Azimuth axis length, ≥ 1 |
| 24 | 4 | E | `int32` LE | Elevation axis length, ≥ 1 |
| 28 | 8 * F | freqs | `double[F]` LE | Frequencies in MHz, sorted ascending |
| 28 + 8F | 8 * A | azs | `double[A]` LE | Azimuths in degrees [0, 360), sorted ascending |
| 28 + 8(F+A) | 8 * E | els | `double[E]` LE | Elevations in degrees [0, 90], sorted ascending |
| 28 + 8(F+A+E) | 2 * F * A * E | gains | `int16[F*A*E]` LE | Gains × 100, row-major; sentinel `Short.MIN_VALUE` |

### 7.2 Header overhead

For the typical full ITU-R pattern (F=28, A=360, E=91):

```
header           = 28 bytes
freqs            = 224 bytes
azs              = 2,880 bytes
els              = 728 bytes
gains            = 1,834,560 bytes
TOTAL            = 1,838,420 bytes (~1.83 MB)
```

For a Type-11 pattern (F=A=1, E=91):

```
header           = 28 bytes
freqs            = 8 bytes
azs              = 8 bytes
els              = 728 bytes
gains            = 182 bytes
TOTAL            = 954 bytes
```

### 7.3 Absolute-dBi convention

All stored gain values are absolute dBi. The factory methods translate from per-source-format conventions at bake time:

- voacapl Type 11 (`.voa`): stored values are **offsets from `parm(1)` Max Gain** (e.g., `const17.voa` has Max Gain = 17 dBi and entries `-43, -12, -7, -2, 0, 0, ...` representing reductions from max). Factory adds Max Gain to entries (preserving sentinel).
- voacapl Type 13 (`.voa` / `.13` / `.t13`): stored values are **absolute dBi** (verified empirically — ITU-R 141-10_0.t13 declares Max Gain = 12.740 and the on-disk grid peaks at exactly 12.740 at line 269). Factory stores as-is.
- voacapl Type 14: presumed absolute by analogy with Type 13 (verify when implementing).
- Analytical bakes: the baker function returns absolute dBi directly.

This convention difference is voacapl's. Hiding it inside the factory methods means downstream code (`GainTable.gainDbi()`) always answers in absolute dBi.

### 7.4 No compression

The format does not specify a compression scheme. Storage at rest is the raw `int16` array; ~1.83 MB per full ITU-R pattern is acceptable for the locked use case. If future scale demands it, the `flags` field is reserved to indicate a compressed variant (e.g., zlib) without breaking format version 1.

---

## 8. `.json` metadata sidecar schema

Locked S10: separate `.json` sidecar next to each `.gtable`.

### 8.1 Schema

```json
{
  "name": "SWWHIP",
  "group": "default",
  "description": "SWWhip.VOA used by VOA for receive antenna",
  "source": {
    "kind": "VOA_TYPE_11",
    "path": "/abs/path/to/swwhip.voa",
    "sha256": "abc123..."
  },
  "baker": {
    "module": "Type11Baker",
    "sha": "def456...",
    "version": "1.0.0"
  },
  "jvoacap_version": "0.1.0",
  "baked_at": "2026-05-17T10:30:00Z",
  "grid": {
    "frequencies_mhz": [10.0],
    "az_count": 1,
    "el_count": 91,
    "az_dependent": false,
    "freq_dependent": false
  },
  "peak_gain_dbi": 0.0,
  "min_freq_mhz": 10.0,
  "max_freq_mhz": 10.0,
  "format_version": 1
}
```

### 8.2 Required vs optional fields

**Required (validation fails on missing):**

- `name`, `group` — must match the file path on disk.
- `source.kind` — one of `VOA_TYPE_0`, `VOA_TYPE_11`, `VOA_TYPE_13`, `VOA_TYPE_14`, `ITU_R_705`, `CEBIK`, `HFMUFES_KOP_N` (N=1..17), `CCIR_JANT_N` (N=1..10, 12), `ANALYTICAL_ISOTROPE`.
- `baker.sha`, `baker.version` — for drift detection.
- `jvoacap_version`, `baked_at`.
- `format_version` — currently 1.

**Optional (warnings if missing, defaults applied):**

- `description` — defaults to "" if missing.
- `source.path`, `source.sha256` — null for analytical antennas (no source file).
- `peak_gain_dbi`, `min_freq_mhz`, `max_freq_mhz`, `grid.*` — derived from the `.gtable` if missing; metadata caches them for catalogue queries.

### 8.3 Source-kind enumeration

| `source.kind` | Description | Source has file? | `source.path` set? | `source.sha256` set? |
|---|---|:---:|:---:|:---:|
| `VOA_TYPE_0` | Isotrope from .voa file | yes | yes | yes |
| `VOA_TYPE_11` | 91-elevation table | yes | yes | yes |
| `VOA_TYPE_13` | 360 × 91 az × el table, single freq | yes | yes | yes |
| `VOA_TYPE_14` | 30 × 91 freq × el table | yes | yes | yes |
| `ITU_R_705` | Multi-freq Type-13 stack | yes (multiple) | yes (directory) | yes (per-file aggregated) |
| `CEBIK` | Cebik .13 format | yes | yes | yes |
| `HFMUFES_KOP_N` | HFMUFES analytical (N=1..17) | no | null | null |
| `CCIR_JANT_N` | CCIR analytical (N=1..10, 12) | no | null | null |
| `ANALYTICAL_ISOTROPE` | Synthetic isotrope | no | null | null |

For analytical kinds, `source.params` carries a canonical JSON representation of the input parameters (Z₀, α, τ, n for LPDA; dipole height for dipole; etc.).

---

## 9. Migration plan

Locked M19: minimum vertical slice first. Six phases, each independently testable and revertible.

### 9.1 Phase A — Foundation (no behaviour change)

**Goal:** introduce `GainTable` and its codec without changing any runtime path.

**Deliverables:**

- `GainTable` record (axes + flat int16 + trilinear lookup + sentinel handling).
- `GainTableCodec` (read/write binary format per §7).
- `GainTableRepository` interface; `FilesystemGainTableRepository` implementation reading from a directory.
- Adapter methods on existing `AntennaModel` and `AntennaModel2D`: `.toGainTable()`.
- Unit tests: round-trip codec, sentinel handling, axis boundaries, all factory methods.

**Verification:** all existing tests pass unchanged. New `GainTable` lookup matches `AntennaModel2D.gainsAbsoluteDbi[az][el]` at integer (az, el) coords for the same source data.

**Risk:** zero — additive code, no call-site changes.

### 9.2 Phase B — Bake CLI shell

**Goal:** implement the CLI and bakers for the three pattern types already supported by the existing reader (Type 0, 11, 13).

**Deliverables:**

- `jvoacap-antennas` sub-command router (Spring Boot `CommandLineRunner` or picocli).
- Sub-commands: `bake`, `bake-dir`, `list`, `show`, `verify`, `remove`.
- Bakers: `Type0Baker`, `Type11Baker`, `Type13Baker`, `MultiFreqType13Baker`.
- Lock-file concurrency for repo writes.
- Manifest regeneration after each bake.
- Shell wrappers: `bin/jvoacap`, `bin/jvoacap-antennas`.

**Verification:** baking `voacapl/itshfbc/antennas/default/swwhip.voa` produces `default/SWWHIP.gtable` + `.json`. Subsequent `verify` reports clean. Drift-test: editing `swwhip.voa` makes `verify` report `source changed`.

**Risk:** low — CLI only, doesn't touch runtime.

### 9.3 Phase C — Wire runtime through `GainTable` (transition mode)

**Goal:** route runtime antenna gain lookup through the repository when available; fall back to existing `*GainCalculator` path otherwise.

**Deliverables:**

- `ModeEnumeratorService` modified to check `GainTableRepository.contains(name)` first; if yes, use `GainTable.gainDbi(...)`; if no, fall back to current dispatch (`AntennaPhysicsService.calculateGain(MufParameters)`).
- Default-layer patterns (`SWWHIP`, `const5`, `const17`) baked at build time and embedded in the jar as classpath resources.
- Startup drift check (warn-and-continue per B3).

**Verification:**

- All existing test cards continue to pass — they reference `[default/SWWHIP.VOA]` etc. which now resolve through the new path.
- `VoacapLReferenceTest` closure-ladder gate stays ≤ 5.0 dB (current 4.60 dB).
- Re-baseline if needed per M17.

**Risk:** medium — runtime path change. Mitigated by the fall-back: any analytical-type request still works via the old path.

### 9.4 Phase D — HFMUFES baker migration

**Goal:** the 17 `*GainCalculator` classes become bakers (called only at bake time); runtime always reads from `GainTable`.

**Deliverables:**

- For each `*GainCalculator` (KOP 1..17): a corresponding `Hfmufes<KOP>Baker` that wraps it and bakes at the standard ITU-R 3–30 MHz × 1° × 1° grid.
- `bake-analytical hfmufes-yagi '{"boomWl":1.5,"directors":3,...}'` CLI invocation.
- A curated default set of HFMUFES bakes for the JSON `/run` API legacy `antType` lookup table (B4 grace period).
- Drop the `*GainCalculator` fall-back path from `ModeEnumeratorService`.

**Verification:**

- `VoacapLReferenceTest` re-baselined per M17.
- New runtime-test layer (`GainTableLookupTest`) added per M16(c).
- Existing 500+ `*GainCalculator` unit tests retained as baker validation (M16(c)).

**Risk:** medium — closure-ladder mean may drift by ±0.05 dB due to 1° quantisation. Re-baseline acceptable.

### 9.5 Phase E — CCIR bakers

**Goal:** port the 11 CCIR analytical formulas (jant 1–10, 12) per B1 scope decision.

**Deliverables:**

- 11 new `Ccir<jant>Baker` classes ported from `antinit2.for` + `antcal.for` + `gainrel.for`.
- Voacapl trace captures for each, for parity testing.
- Sub-command: `bake-analytical ccir-horiz-lp '{"L1":...,"Ln":...,"h1":...,"hn":...,"dc":...,"z0":...}'`.

**Verification:** per-CCIR-type parity test against captured voacapl gain at trace-pinned (az, el) coords, within ±0.5 dB ratchet.

**Risk:** medium — new physics ports require trace captures.

### 9.6 Phase F — JSON `/run` API migration

**Goal:** complete the deprecation of `antType` in favour of `antennaName`.

**Deliverables:**

- Deprecation warning for requests carrying `antType`.
- One-release grace period during which both work.
- Documentation update for the OpenAPI spec.
- Removal of `antType` support after grace period.

**Risk:** low for jvoacap-internal callers; depends on external integrations.

---

## 10. Test fixtures

Locked: `@BeforeAll`-style baker run at test setup.

### 10.1 Strategy

The test suite uses two source antenna fixtures already in the repo (TD-105):

- `src/test/resources/voacap-reference/antennas/sbrr216a.13` (Cebik Bruce array, 1.85 MHz)
- `src/test/resources/voacap-reference/antennas/141-10_0.t13` (ITU-R AHRS-2-1, 10 MHz)

Plus the voacapl source antennas, which need to be reachable for the Phase-A test that bakes them:

- `voacapl/itshfbc/antennas/default/swwhip.voa`
- `voacapl/itshfbc/antennas/default/const5.voa`
- `voacapl/itshfbc/antennas/default/const17.voa`

### 10.2 Setup

A static `@BeforeAll` method (in a shared test base class or JUnit extension) runs before any antenna-touching test:

1. Compute the test-repo path: `target/test-antennas/` (gitignored).
2. Walk `src/test/resources/voacap-reference/antennas/` and the voacapl default antennas.
3. For each source file, run the appropriate baker into `target/test-antennas/`.
4. Set `-Djvoacap.antennas.path=target/test-antennas` for the test JVM.

### 10.3 Why not pre-baked fixtures

Two alternatives considered:

- Pre-baked `.gtable` checked into git: rejected — git diff becomes opaque on every formula change; CI runs would have to re-bake-and-compare.
- Treat voacapl source tree as the implicit test repo: rejected — requires the voacapl checkout to be at a known path, fragile across machines.

`@BeforeAll` bake is reproducible, self-contained, and exercises the bake CLI as part of the test suite.

### 10.4 Test categories

| Test class | Source antennas | Purpose |
|---|---|---|
| `GainTableTest` | synthetic | Codec round-trip, sentinel, axis boundaries |
| `Type11BakerTest` | `swwhip.voa`, `const5.voa`, `const17.voa` | Bake Type-11 sources, verify gain values match the source file at integer-degree elevations |
| `Type13BakerTest` | `sbrr216a.13` | Bake Type-13 source, verify spot values |
| `MultiFreqType13BakerTest` | `141-10_0.t13` (one freq) | Bake single-freq variant, verify shape |
| `GainTableLookupTest` (post-Phase D) | baked HFMUFES analytical | Exercise the runtime path against analytical bakes |
| `HfmufesBakerParityTest` (post-Phase D) | each KOP | Bake at standard params; verify against existing `*GainCalculator` formulas |

---

## 11. Packaging and distribution

### 11.1 Single jar with sub-command routing

The build produces one fat jar: `jvoacap-X.Y.Z-SNAPSHOT.jar`.

Two shell wrappers in `bin/`:

```
bin/jvoacap                  # exec java -jar $JVOACAP_JAR "$@"
bin/jvoacap-antennas         # exec java -jar $JVOACAP_JAR antennas "$@"
```

Invocations:

- `jvoacap` (no args) → start Spring Boot HTTP server on port 8080 (default).
- `jvoacap antennas bake source.voa` → run bake CLI.
- `jvoacap antennas verify` → run drift check.
- `jvoacap-antennas bake source.voa` → equivalent to above (alternative entry point).

Routing implementation: a main-class-level switch on the first argument. If it's `antennas` (or another future sub-command), strip it and delegate to the CLI module. Otherwise, fall through to the Spring Boot application.

### 11.2 Why one jar

- Avoids Maven multi-module split (significant refactor for a research tool).
- Both the server and the CLI need the same physics + I/O code; splitting would duplicate dependencies.
- One artifact to ship, one version to track.
- The CLI startup cost (Spring Boot context init ~1s) is a real annoyance for short bake invocations, but acceptable for the locked scope. If it becomes a bottleneck, the CLI sub-command router can skip Spring Boot initialisation entirely (~100ms cold start).

### 11.3 Defaults layer in the jar

The three default-layer patterns (SWWHIP, const5, const17) are baked at jvoacap build time (during Maven `package`) and packaged into the jar as classpath resources under `BOOT-INF/classes/antennas/default/`. At runtime, `FilesystemGainTableRepository` falls back to classpath resources when not found in the user layer.

### 11.4 Maven build steps

```
mvn clean package                    # standard build
  -> compiles all code
  -> runs unit tests (with @BeforeAll baker)
  -> runs the bake CLI on voacapl/itshfbc/antennas/default/*.voa
     to produce src/main/resources/antennas/default/*.gtable + .json
  -> packages everything into jvoacap-X.Y.Z-SNAPSHOT.jar
```

The "bake voacapl defaults into resources" step is a new Maven plugin invocation (or a `<exec>` of the freshly-built CLI) that runs after `compile` and before `package`. If the source `.voa` files aren't present (voacapl not checked out at the expected path), the step skips with a warning and the defaults layer ships empty — runtime behaves as if no defaults exist.

---

## 12. Versioning

Locked E23: major version bump on `.gtable` binary format change.

### 12.1 Semver rules

`jvoacap` and `jvoacap-antenna-patterns` are versioned together for simplicity (single Maven artifact tree).

- **Major bump (X.0.0):** `.gtable` binary format change (e.g., new header fields, different gain encoding). All existing baked patterns must be rebaked. Format version field in the binary header advances.
- **Minor bump (x.Y.0):** new baker (new `source.kind`), new sub-command, new physics in a baker (changes the baked output but format stays compatible). Existing patterns continue to load; users may want to rebake to benefit from physics fixes.
- **Patch bump (x.y.Z):** bug fixes that don't change baker outputs or format.

### 12.2 Detecting incompatibility

`GainTableCodec.read()` checks the magic bytes + version field. On version mismatch:

- Version newer than the running build → fail with `UnsupportedFormatVersion`. User must upgrade jvoacap.
- Version older than current → attempt to load; current implementation supports version 1 only. Future versions may include forward-compat readers.

`baker.sha` in metadata sidecars tracks the specific baker module commit. A change here without a version bump means the formula changed — drift detection (§5.4) reports `baker changed` and the user is prompted to rebake.

---

## 13. Decisions reference (locked 2026-05-17)

For traceability. All decisions made through the planning conversation:

| # | Topic | Locked value |
|---|---|---|
| B1 | Catalogue scope | Pattern types + HFMUFES + CCIR (no IONCAP / NOSC / Harris) |
| B2 | Repository location | Layered: defaults (in jar) + user filesystem |
| B3 | Drift handling default | Warn-and-continue at startup |
| B4 | JSON `/run` API | Deprecate `antType`, require `antennaName`, one-release grace period |
| S5 | Default catalogue contents | voacapl 3: SWWHIP, const5, const17 |
| S6 | Multi-freq interpolation | Linear in MHz |
| S7 | Card antenna syntax | Both `[default/SWWHIP.VOA]` and `[default/SWWHIP]` accepted |
| S8 | Repository directory structure | Hierarchical (`antennas/<group>/<name>.gtable`) |
| S9 | `.gtable` binary format | Magic `JVOAGT01` + version + axes + int16 LE gains |
| S10 | Metadata location | Separate `.json` sidecar |
| S11 | Sentinel encoding | `Short.MIN_VALUE` |
| S12 | Out-of-range frequency query | Log-warn and clamp |
| S13 | Sentinel propagation in lookup | Collapse elevation axis to el-hi corner |
| S14 | CLI shape | Separate logical binary via shell wrappers |
| M15 | Existing `AntennaModel*` | Keep as factory inputs |
| M16 | 17 `*GainCalculator` tests | Keep both layers (existing as baker tests + new runtime tests) |
| M17 | Closure-ladder re-baseline | Once after migration |
| M18 | `AreaCoverageParityTest` | Stays as-is |
| M19 | First milestone | Minimum vertical slice (Phase A) |
| E20 | Bake-tool concurrency | Lock file in repo |
| E21 | Source files in repo | Source path stored in metadata only |
| E22 | CI enforcement | None |
| E23 | Versioning | Major bump on `.gtable` format change |
| E24 | Documentation | `docs/antennas.md` + LaTeX `\chapter{Antennas}` |
| T1 | Test fixtures | `@BeforeAll` baker into `target/test-antennas/` |
| T2 | User-repo path default | `./antennas/` (CWD-relative); configurable via `-Djvoacap.antennas.path` |
| T3 | Packaging | Single jar with sub-command routing + shell wrappers |

---

## 14. Open items and future work

Items explicitly deferred or out-of-scope:

- **IONCAP, NOSC, Harris analytical types.** Per B1, only CCIR is in scope alongside HFMUFES. The remaining ~10 IONCAP types and the Harris external-file path remain unported. Add a new baker module if a user has a real need.
- **CI drift gate.** Per E22, no CI step verifies that committed patterns are still fresh. Manual `jvoacap-antennas verify` is the recommended workflow.
- **Compression for `.gtable` at rest.** Format version 1 stores raw `int16`. Reserved `flags` field allows a future compressed variant (zlib reduces ~1.83 MB → ~400 KB). Deferred until storage becomes a measurable concern.
- **Auto-bake-and-cache for ad-hoc patterns.** Explicitly rejected during planning. Workflow friction accepted as the cost of explicit reproducibility.
- **Multi-user / Postgres backing.** Out of scope; jvoacap is single-user. If the project ever ships as a service, revisit with the Postgres bytea schema sketched in the planning conversation.
- **Polarisation axis.** No HF antenna format in scope carries polarisation as a distinct dimension. The `GainTable` shape (3 axes) is fixed; adding polarisation would require a format-version-2 binary.
- **0.1° grid resolution.** Locked at 1° per voacapl's convention. A finer grid would 100× memory; deferred.

---

## 15. Glossary

| Term | Meaning |
|---|---|
| **`.gtable`** | Binary file holding one baked antenna pattern (magic + version + axes + int16 gains). |
| **bake** | Convert a source antenna representation (voacapl `.voa`, ITU-R `.t13`, analytical params) into a `GainTable` and write to the repository. |
| **GainTable** | Internal Java record representing one antenna's gain pattern as a 3-axis tensor with int16 fixed-point storage. |
| **jant** | voacapl's antenna type code (`parm(2)` in the file header). Spans 0–47, 48, 90+. |
| **KOP** | jvoacap's antenna type code, follows voacapl HFMUFES numbering (1–17). Distinct from `jant`. |
| **multi-freq Type-13** | A collection of single-frequency Type-13 files (e.g., ITU-R Rec.705 catalogue) baked into one `GainTable` with F ≥ 1. |
| **repository** | Filesystem directory containing baked `.gtable` + `.json` files. Layered: defaults from jar + user from `-Djvoacap.antennas.path`. |
| **sentinel** | `Short.MIN_VALUE` in the stored gain array, indicating "below horizon, no signal". |
| **source SHA-256** | Hash of the input file used to detect when a baked pattern has drifted from its source. |
| **stale baked pattern** | A `.gtable` whose recorded source SHA-256 no longer matches the current source file or whose baker SHA no longer matches the running build. |
| **trilinear interpolation** | The lookup algorithm for `GainTable.gainDbi(f, a, e)`: bracket each axis, sample 8 corners of the tensor, weighted-sum. |
| **voacapl** | James Watson's GFortran port of NTIA's VOACAP. The reference implementation against which jvoacap is parity-tested. |

---

## 16. Document control

- **Created:** 2026-05-17
- **Status:** Locked
- **Implementation tracking:** Phase A through Phase F as defined in §9
- **Companion document:** A `\chapter{Antennas}` section in `docs/jvoacap_user_manual.tex` covers the user-facing workflow (bake CLI usage, repository layout, troubleshooting) as derived content from this spec.
- **Change history:**
  - 2026-05-17 — Initial lock-in after the planning conversation; all 27 decisions captured in §13.
