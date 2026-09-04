---
name: gradle-cache-source-lookup
description: This skill should be used when an AndroidX / Jetpack Compose / Kotlin library API signature is uncertain — e.g. a compile error reveals a wrong constructor, an abstract class being instantiated directly, or a method name/parameter mismatch. Instead of guessing from memory or stale docs, extract the real source .kt from the Gradle cache *-sources.jar and read the exact signature before fixing.
agent_created: true
---

# Gradle Cache Source Lookup

## Overview

Verify exact API signatures of AndroidX / Compose / Kotlin dependencies by reading the **real source code** shipped in the Gradle cache `*-sources.jar`, rather than relying on memory or outdated documentation. This eliminates a whole class of "guessed the signature wrong" compile errors and avoids extra build round-trips.

## When To Use

- A Kotlin/Compose compile error reveals an API mismatch: `Class 'X' is not abstract and does not implement abstract members`, `Cannot create an instance of an abstract class`, `overrides nothing`, `Argument type mismatch`, `No value passed for parameter`.
- Unsure about a library class constructor signature, interface method list, or whether a class is `abstract` / `sealed` / `open`.
- Before fixing any dependency-API compile error — confirm the real signature first.

## Workflow

### Step 1 — Locate the sources jar

Gradle caches sources as:
`~/.gradle/caches/modules-2/files-2.1/<group-path>/<artifact>/<version>/<hash>/<artifact>-<version>-sources.jar`

Where `<group-path>` is the Maven group with dots replaced by slashes. Examples:

| Dependency | group-path | artifact | version |
|---|---|---|---|
| Compose UI text (Android) | `androidx.compose.ui` | `ui-text-android` | `1.11.2` |
| Compose UI graphics | `androidx.compose.ui` | `ui-graphics` | `1.11.2` |
| Activity | `androidx.activity` | `activity` | `1.9.0` |

If unsure which artifact a class lives in, the class's package root (`androidx.compose.ui.text.font`) maps to the `androidx.compose.ui` group and `ui-text` / `ui-text-android` artifacts. **Platform-specific code (Android) lives in the `-android` artifact** (`ui-text-android`), common code in `ui-text`.

### Step 2 — Find and extract the `.kt` file

Multiplatform sources use layout prefixes: `androidMain/...` for Android-specific classes, `commonMain/...` for common. Use the bundled helper:

```bash
python <skill>/scripts/extract_source.py \
  --jar "~/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-text-android/1.11.2/<hash>/ui-text-android-1.11.2-sources.jar" \
  --match "AndroidFont" --out /tmp/lookup
```

The helper lists all matching entries and extracts them to `--out`. Then **Read** the extracted `.kt` file.

If the jar path/hash is unknown, locate it with a filesystem search for `*-sources.jar` under `~/.gradle/caches` and grep the namelist for the class.

### Step 3 — Read the real signature, then fix

Open the extracted source and confirm:
- Constructor parameter order, names, and which params have defaults.
- Interface method signatures (names, `suspend`, parameter types — note they may take the concrete class, e.g. `AndroidFont`, not a base `Font`).
- Whether the class is `abstract` (requires a subclass) or `sealed`/`open`.

Apply the fix to match the real source exactly. Prefer reading the source over guessing even when a quick fix "looks right" — subtle differences (e.g. `loadBlocked` vs `loadBlocking`, `Font(resId)` vs `Font(file: File)`) are exactly what cause repeated compile failures.

## Concrete Case Study

See `references/gradle_sources_lookup.md` for the real `AndroidFont` (Compose UI 1.11.2) signature that motivated this skill, including the abstract-class-subclass + `TypefaceLoader.loadBlocking/awaitLoad` pattern.

## Resources

### scripts/
- `extract_source.py` — list and extract `.kt` entries from a sources jar by substring match.

### references/
- `gradle_sources_lookup.md` — worked example: the `AndroidFont` API signature from `ui-text-android-1.11.2-sources.jar`.
