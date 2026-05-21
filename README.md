# JAntenna

Antenna pattern library extracted from [jvoacap](https://github.com/BadRumplestiltskin/jvoacap).

Provides a unified `GainTable` representation (frequency × azimuth × elevation tensor), readers for the VOACAPL `.voa` family, analytical bakers for all 17 HFMUFES KOP antenna types, a bake CLI, and a Swing GUI with live pattern visualisation.

## Requirements

- JDK 21
- Maven 3.9+

## Build

```
mvn install
```

The build also bakes the default antenna set (`isotrope-0dbi`, `swwhip`, `const5`, `const17`) into `target/classes/antennas/default/`.

## CLI

The bake CLI is `AntennasCli`. Run it directly from Maven or wire it into your launcher:

```
mvn exec:java -Dexec.mainClass=com.jantenna.cli.AntennasCli -Dexec.args="<sub-command> [args]"
```

### Sub-commands

| Sub-command | Usage | Description |
|---|---|---|
| `bake` | `bake <file> [group]` | Bake one `.voa` / `.13` / `.t13` source file into the repository |
| `bake-dir` | `bake-dir <dir> [group]` | Recursively bake every recognised source file under a directory |
| `bake-isotrope` | `bake-isotrope <name> <gain-dbi> <freq-mhz> [group]` | Bake an analytical isotrope |
| `bake-hfmufes` | `bake-hfmufes <name> <params-json> [group]` | Bake an analytical HFMUFES antenna (KOP 1–17) |
| `list` | `list [--group <name>]` | List all patterns in the repository |
| `show` | `show <pattern-name>` | Print metadata and grid summary for one pattern |
| `verify` | `verify` | SHA-256 drift check across all baked patterns |
| `remove` | `remove <pattern-name>` | Delete a pattern's `.gtable` and `.json` from the repository |

The repository path defaults to `./antennas/`. Override with `-Djvoacap.antennas.path=/some/dir`.

### Quick bake example

```
# Bake a single VOACAPL source file into the "user" group
mvn exec:java -Dexec.mainClass=com.jantenna.cli.AntennasCli \
  -Dexec.args="bake /path/to/rhombic.voa user"

# Bake an entire ITU-R T.13 catalogue directory
mvn exec:java -Dexec.mainClass=com.jantenna.cli.AntennasCli \
  -Dexec.args="bake-dir /path/to/t13-files itur705"
```

## GUI

Launch the Swing GUI:

```
mvn exec:java -Dexec.mainClass=com.jantenna.gui.JAntennaApp
```

The window opens maximised with a horizontal split:

- **Left — Bake tabs**
  - *Single File*: pick a source file and output folder, set group/name, click **Bake**. A **View Pattern** button appears on success to open the result immediately in the viewer.
  - *Batch*: pick an input folder and output folder, click **Bake All**. A per-file ✓/✗ result table updates live; failures are skipped and the rest continue.
- **Right — Pattern Viewer**: open any `.gtable` via **File > Open .gtable** or the viewer's own **Open…** button.
  - Shared frequency slider at the top.
  - **H-Plane** (azimuth cut at fixed elevation) and **V-Plane** (elevation cut at fixed azimuth) side by side.
  - Each panel has its own angle slider and a **Polar / Cartesian** toggle. The **View > Toggle Polar/Cartesian** menu item switches both panels at once.
  - Polar charts use compass orientation (0° = North, clockwise) with a 40 dB dynamic range on the radial axis.

Last-used input and output directories are persisted across sessions (Java `Preferences` API). Recent `.gtable` files appear under **File > Recent Files**.

## Library

### Maven coordinates

```xml
<dependency>
    <groupId>com.jantenna</groupId>
    <artifactId>jantenna</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### GainTable

The central type is `com.jantenna.GainTable` — an immutable record holding a 3-axis gain tensor stored as `short[]` (gain × 100, 0.01 dB resolution, sentinel `Short.MIN_VALUE` = below horizon).

```java
// Trilinear lookup
double gainDbi = table.gainDbi(freqMHz, azimuthDeg, elevationDeg);

// Axis metadata
double[] freqs = table.frequenciesMHz();   // F entries
double[] azs   = table.azimuthsDeg();      // A entries, [0, 360)
double[] els   = table.elevationsDeg();    // E entries, [0, 90]
```

Flat index formula for direct access: `flat[f * A * E + a * E + e]`.

### File I/O

```java
// Read a baked .gtable
GainTable table = GainTableCodec.read(Path.of("antennas/user/rhombic.gtable"));

// Write (atomic — writes to .tmp then renames)
GainTableCodec.writeAtomic(table, targetPath);
```

The `.gtable` binary format is little-endian with an 8-byte magic header `JVOAGT01`. See [`GainTableCodec`](src/main/java/com/jantenna/GainTableCodec.java) for the full layout.

### Reading source files

```java
// Detect jant type and return the right baker
AntennaBaker baker = BakerDispatcher.forFile(Path.of("rhombic.voa"));
AntennaBaker.BakeResult result = baker.bake(source, "user", "rhombic");
// result.table()    → GainTable
// result.metadata() → AntennaMetadata (written alongside as .json sidecar)
```

### Analytical bakers (HFMUFES KOP)

```java
HfmufesCalculatorFactory factory = new HfmufesCalculatorFactory();
AntennaGainCalculator calc = factory.create(6);   // KOP 6 = Curtain Array
// calc == null for KOP 10 (PreStored — use BakerDispatcher instead)

HfmufesBakeSpec spec = new HfmufesBakeSpec(/* kop, sigma, epsilon, ... */);
AntennaBaker baker = new HfmufesAnalyticalBaker(calc, spec);
```

Supported KOPs: 1 Rhombic, 2 Vertical Monopole, 3 Half-Wave Dipole, 4 Yagi, 5 Vertical Dipole, 6 Curtain Array, 7 Sloping Vee, 8 Inverted L, 9 Sloping Rhombic, 11 Sloping Long Wire, 12 Constant Gain, 13 Log-Periodic, 14 Tilted Dipole, 15 Half Rhombic, 16 Double Rhomboid, 17 Vertical Radial-Ground Monopole. KOP 10 (PreStored) is excluded by design — bake the referenced `.voa` file directly via `BakerDispatcher`.

### Repository access at runtime

```java
// Filesystem repository (baked .gtable files on disk)
GainTableRepository repo = new FilesystemGainTableRepository(Path.of("antennas"));

// Layered: classpath defaults + filesystem overrides
GainTableRepository layered = new LayeredGainTableRepository(
        new ClasspathGainTableRepository(),
        new FilesystemGainTableRepository(repoPath));

Optional<GainTable> table = layered.load("default/isotrope-0dbi");
```

## Package layout

```
com.jantenna
├── GainTable            Immutable gain tensor record
├── GainTableCodec       Binary .gtable read/write
├── AntennaMetadata      JSON sidecar record
├── MetadataCodec        JSON sidecar read/write
├── baker/               Bake pipeline (BakerDispatcher, HfmufesAnalyticalBaker, …)
├── cli/                 AntennasCli and sub-command handlers
├── gui/                 Swing GUI (JAntennaApp, MainFrame, viewer, bake panels)
├── math/                Quadrature integrators, SpecialFunctions
├── physics/             GainCalculators (KOP 1–17), AntennaConstants
├── reader/              VoaAntennaReader (Type 11/13 source parser)
└── repository/          FilesystemGainTableRepository, LayeredGainTableRepository
```

## Testing

```
mvn test
```

107 tests covering codec round-trips, all 17 KOP calculators, mutual impedance, the CLI, and the repository stack.

## Background

See [`docs/antennas.md`](docs/antennas.md) for the full design specification: why the unified `GainTable` representation was chosen, the `.gtable` binary format layout, the repository contract, and the migration history from jvoacap's previous ad-hoc antenna handling.
