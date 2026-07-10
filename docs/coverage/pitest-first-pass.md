# PITest Mutation Testing — First-Pass Triage

**Date:** 2026-07-10
**Tool:** PITest (PIT) via the `info.solidsoft.pitest` Gradle plugin — **report-only / advisory**, NOT wired into `check`/`build`.
**Run:** `./gradlew pitest` (locally: `sh gradlew pitest`, the wrapper is not executable). Report: `build/reports/pitest/index.html` (+ `mutations.xml`).

## Why this exists

The non-GUI core carries high JaCoCo *line* coverage (85%+ gate). Line coverage only proves a
line **executed** under test — not that any assertion **pins its behavior**. Mutation testing
seeds faults ("mutants") into the bytecode, re-runs the covering tests per mutant, and reports
which mutants **survived** (no test noticed). A surviving mutant on a JaCoCo-covered line is the
actionable signal: *covered but not asserted*. This is measurement + a backlog only — no tests
were changed in this pass.

## Scope of this pass

PIT re-runs the covering tests per mutant, so **scope is the dominant cost lever**. This first
pass targets the deterministic, fast, low-CDK core:

**Included** (`targetClasses` + `targetTests`):
`model.util.*`, `model.data.*`, `model.io.*`, `preference.*`, `configuration.*`

**Deferred** (CDK/algorithm-heavy — enable in a later pass once runtime budget allows):
`model.fragmentation.*`, `model.depict.*`, `model.settings.*`

**Excluded** (mutation-hostile / slow): `controller.*`, `gui.*`, `integration.*`, `main.*`, `message.*`.

> Note on `model.io`: the research flagged `ExporterTest` (Mockito `MockedStatic`) as a possible
> hang risk under PIT. Empirically it ran cleanly (slowest single test 723 ms, zero minion
> hangs), so **no test exclusion was needed** and `model.io` is part of this first pass.

## Headline result

| Metric | Value |
|--------|-------|
| Mutations generated | 1098 |
| Killed | 790 (72%) |
| **Test strength** (killed / (killed+survived), excl. no-coverage) | **80%** |
| Line coverage of mutated classes (PIT-internal) | 2208/2567 (86%) |
| Mutations with no coverage | 114 |
| Wall-clock | ~1 min 20 s (4 threads) |

## Empirical resolution of research assumptions

- **A1 — `pitest-junit5-plugin:1.2.3` vs JUnit Platform 1.11.x (Jupiter 5.11.4):** ✅ works. 60
  test classes discovered, 2098 tests run across mutants. No "no tests found" / `NoClassDefFound`.
  No fallback to 1.2.2 needed.
- **A2 — Kotlin-DSL `.set(...)` lazy `Property` accessors:** ✅ all fields
  (`targetClasses`, `outputFormats`, `threads`, `timeoutFactor`, `jvmArgs`, …) accept `.set(...)`.
  No plain-assignment fallback needed. Config-cache stored cleanly (A5 OK).
- **A4 — `ExporterTest` MockedStatic misbehaving under PIT:** did **not** materialize; no exclusion.

## Per-package mutation score (survivors = assertion gaps)

Score = killed / (killed + survived), excluding NO_COVERAGE.

| Package | Mutations | Killed | Survived | No-coverage | Score |
|---------|-----------|--------|----------|-------------|-------|
| `model.data` | 67 | 61 | 6 | 0 | **91.0%** |
| `preference` | 354 | 271 | 47 | 35 | **85.0%** |
| `model.util` | 321 | 244 | 44 | 32 | **84.4%** |
| `model.io` | 350 | 204 | 95 | 47 | **67.3%** |
| `configuration` | 6 | 4 | 2 | 0 | **66.7%** |

`model.io` (Importer/Exporter) is the weakest by test strength — many survivors are I/O side
effects and molecule-counter arithmetic that tests execute but never assert on. `configuration`
looks low only because it has a tiny mutant population (6).

Survivor counts by class (top): Importer 47, Exporter 42, PreferenceContainer 27, ChemUtil 21,
LogUtil 14, RGBColorPreference 7, FileUtil 5, FragmentDataModel 4.

## Ranked triage — top survivors to assert next

Ranked by *business/correctness value* (core cheminformatics + data-model contracts first;
cosmetic PDF layout and logging side-effects deprioritized).

| # | Class:Line | Method | Mutator | Why it likely survived / what to assert |
|---|-----------|--------|---------|------------------------------------------|
| 1 | `ChemUtil:436` | `fixRadicals` | Math (`-`→`+`) + ConditionalsBoundary + NegateConditionals | Radical/valency correction arithmetic runs but the corrected atom state is never asserted. Assert resulting valency/formal-neighbour-count on a known radical input. |
| 2 | `ChemUtil:506` | `fixAromaticNitrogenAndCreateSMILES` | VoidMethodCall (removed `SmilesParser::kekulise`) | Kekulization side-effect unverified. Assert the produced SMILES for an aromatic-N case differs when kekulise is/ isn't applied. |
| 3 | `ChemUtil:357/365/382/428/475` | `saturateWithHydrogen` / `checkAndCorrectElectronConfiguration` | NegateConditionals | Guard branches execute but neither branch outcome is asserted. Add inputs that exercise both sides of each guard and assert the container state. |
| 4 | `CollectionUtil:149` | `calculateInitialHashCollectionCapacity` | ConditionalsBoundary | Pure-logic boundary (capacity threshold) unasserted — cheap, high-value fix. Assert capacity at the exact boundary load factor. |
| 5 | `StringSortWrapper:149` | `hashCode` | Math (`+`→`-`, `*`→`/`) | hashCode formula unpinned. Assert equal wrappers share a hashCode and the value is stable/expected. |
| 6 | `FragmentDataModel:281/294/306/321` | `setAbsoluteFrequency` / `setMoleculeFrequency` / `setAbsolutePercentage` / `setMoleculePercentage` | ConditionalsBoundary | Validation boundaries in the frequency/percentage setters unasserted. Assert rejection/acceptance exactly at the boundary value. |
| 7 | `MoleculeDataModel:350` | `setKeepAtomContainer` | NegateConditionals | Guard flips silently. Assert the atom-container retention flag effect on both branches. |
| 8 | `PreferenceContainer:770/800/801` | `compareTo` / `hashCode` | PrimitiveReturns (→0) + Math | Ordering/equality contract not asserted. Assert `compareTo` sign for ordered pairs and hashCode consistency with `equals`. |
| 9 | `PreferenceContainer:454/477/500/518` | `add` / `replace` / `delete` | BooleanTrueReturn | Success/failure boolean return is ignored by tests. Assert the boolean result for both the success and the reject (duplicate/missing) paths. |
| 10 | `RGBColorPreference:257/259` | `setContent` | Math (`/`→`*`) | Colour-channel normalization arithmetic unasserted. Assert stored channel values for a known RGBA input. |
| 11 | `RGBColorPreference:269/282` | `setAlpha` | ConditionalsBoundary | Alpha range validation boundary unasserted. Assert accept/reject at alpha min/max. |
| 12 | `Importer:433/443/519 + 445` | `importSDFile` / `importPDBFile` | Increments / Math | Molecule-index/counter arithmetic runs but resulting molecule names/count aren't asserted. Assert imported molecule count and per-molecule naming for a multi-record SDF/PDB. |
| 13 | `Importer:537–545` | `findMoleculeName` (+ lambdas) | Boolean*Return / NegateConditionals | Name-detection predicates unpinned. Assert the chosen name across the fallback chain (title present / blank / absent). |
| 14 | `Importer:276` | `parse` | EmptyObjectReturnVals (→ emptyList) | Returning an empty list instead of parsed molecules goes unnoticed. Assert the parsed list is non-empty and has expected size. |
| 15 | `Exporter:1131` | `convertToITextImage` | NullReturnVals (→ null) | Null image return unasserted. Assert a non-null image is produced for a valid fragment depiction. |
| 16 | `FileUtil:207/233` | `createDirectory` / `createEmptyFile` | BooleanTrueReturn | Return value (created vs. failed) ignored. Assert the boolean result and the filesystem effect. |

### Deprioritized clusters (real survivors, low ROI)

- **`Exporter` PDF-layout `VoidMethodCall`s** (`setFixedHeight`, `setHorizontalAlignment`,
  `addElement`, `setWidths`, lines ~641–810): cosmetic PDF cell formatting; visually asserting
  these is expensive and brittle. Skip unless a layout regression is reported.
- **`LogUtil` (14 survivors):** logging-environment side effects (uncaught-handler install, log-file
  housekeeping). Low correctness value and awkward to assert; revisit only if log-rotation bugs surface.
- **`Exporter`/`Importer` CDK writer/reader `setSetting`/`setSkip`/`kekulize` VoidMethodCalls:** many
  are IO-writer configuration side effects; assert via round-trip output content where practical
  (covered partly by the deferred `integration.*` round-trip tests).

## Next steps (follow-up task, not this one)

1. Strengthen assertions for triage rows 1–11 (core `model.util` / `model.data` / `preference`
   logic) — cheapest, highest-signal wins.
2. Address `model.io` counter/naming survivors (rows 12–15) with fixture-based round-trip assertions.
3. Second PIT pass adding `model.fragmentation.*` / `model.depict.*` / `model.settings.*` once a
   runtime/CI budget is agreed (expect minutes, CDK-heavy). Keep PIT report-only and out of the gate.
